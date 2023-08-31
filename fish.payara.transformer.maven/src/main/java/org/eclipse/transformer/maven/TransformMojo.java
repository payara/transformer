/********************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: (EPL-2.0 OR Apache-2.0)
 ********************************************************************************/

// Copyright (c) 2020 Contributors to the Eclipse Foundation
// Copyright (c) 2022-2023 Payara Foundation and/or its affiliates

package org.eclipse.transformer.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.transformer.Transformer;
import org.eclipse.transformer.jakarta.JakartaTransformer;

/**
 * This is a Maven plugin which runs the Eclipse Transformer on build artifacts
 * as part of the build.
 */
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM, defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, threadSafe = true)
public class TransformMojo extends AbstractMojo {

	private static final String OUTPUT_PREFIX = "output_";

	private final static String TARGET_AS_ORIGIN = "transformed";

	private static final String SELECTED_SOURCE = "selectedSource";

	private static final String SELECTED_TARGET= "selectedTarget";

	private final static Logger log = Logger.getLogger(TransformMojo.class.getName());

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject		project;

	@Parameter(defaultValue = "false", property = "transformer-plugin.invert", required = true)
	private Boolean				invert;

	@Parameter(defaultValue = "true", property = "transformer-plugin.overwrite", required = true)
	private Boolean				overwrite;

	@Parameter(defaultValue = "true", property = "transformer-plugin.mainSource", required = true)
	private Boolean				mainSource;

	@Parameter(defaultValue = "true", property = "transformer-plugin.testSource", required = true)
	private Boolean				testSource;

	@Parameter(property = "transformer-plugin.renames", defaultValue = "")
	private String				rulesRenamesUri;

	@Parameter(property = "transformer-plugin.versions", defaultValue = "")
	private String				rulesVersionUri;

	@Parameter(property = "transformer-plugin.bundles", defaultValue = "")
	private String				rulesBundlesUri;

	@Parameter(property = "transformer-plugin.direct", defaultValue = "")
	private String				rulesDirectUri;

	@Parameter(property = "transformer-plugin.per-class-constant", defaultValue = "")
	private String rulesPerClassConstantUri;

	@Parameter(property = "transformer-plugin.selectedSource", defaultValue = "")
	private String selectedSource;

	@Parameter(property = "transformer-plugin.selectedTarget", defaultValue = "")
	private String selectedTarget;

	@Parameter(property = "transformer-plugin.xml", defaultValue = "")
	private String				rulesXmlsUri;

	@Parameter(defaultValue = TARGET_AS_ORIGIN)
	private String				classifier;

	@Parameter(defaultValue = "${project.build.directory}", required = true)
	private File				outputDirectory;

	@Component
	private MavenProjectHelper	projectHelper;

	/**
	 * Main execution point of the plugin. This looks at the attached artifacts,
	 * and runs the transformer on them.
	 *
	 * @throws MojoFailureException Thrown if there is an error during plugin
	 *             execution
	 */
	@Override
        public void execute() throws MojoFailureException {
             final Transformer transformer = getTransformer();

             if (mainSource) {
                 final Artifact[] sourceArtifacts = getSourceArtifacts();
                 for (final Artifact sourceArtifact : sourceArtifacts) {
                     transform(transformer, sourceArtifact);
                 }
             }
             if (testSource) {
                 File testDirectory = getTestDirectory();
                 if (testDirectory.exists()) {
                     transform(transformer, testDirectory);
                 }

				 transform(transformer, Paths.get(project.getBuild().getSourceDirectory()).toFile());
             }
         }

	/**
	 * This runs the transformation process on the source artifact with the
	 * transformer provided. The transformed artifact is attached to the
	 * project.
	 *
	 * @param transformer    The Transformer to use for the transformation
	 * @param sourceArtifact The Artifact to transform
	 * @throws MojoFailureException if plugin execution fails
	 */
	public void transform(final Transformer transformer, final Artifact sourceArtifact) throws MojoFailureException {

		final String sourceClassifier = sourceArtifact.getClassifier();
		final String targetClassifier = (sourceClassifier == null || sourceClassifier.length() == 0) ? this.classifier
			: sourceClassifier + "-" + this.classifier;

		final File targetFile = new File(outputDirectory, sourceArtifact.getArtifactId() + "-" + targetClassifier + "-"
			+ sourceArtifact.getVersion() + "." + sourceArtifact.getType());

		//processing new parameters to select source file and target directory to execute transform operation
		// from maven command line
		log.info("Processing custom parameters for source and target file");
		List<String> args = processCustomParameters(sourceArtifact.getFile().getAbsolutePath(), targetFile.getAbsolutePath());

		transformer.setArgs(args.toArray(new String[0]));
		log.info("configured Args:" + args);
		int rc = transformer.run();

		if (rc != 0) {
			throw new MojoFailureException("Transformer failed with an error: " + Transformer.RC_DESCRIPTIONS[rc]);
		}

		if (TARGET_AS_ORIGIN.equals(classifier) && !isSourceAndTargetAvailable()) {
			try {
				if (sourceArtifact.getFile().isDirectory()) {
					FileUtils.deleteDirectory(sourceArtifact.getFile());
				} else {
					targetFile.delete();
				}
				targetFile.renameTo(sourceArtifact.getFile());
			} catch (IOException ex) {
				throw new MojoFailureException("Transformer failed", ex);
			}
		} else {
			projectHelper.attachArtifact(project, sourceArtifact.getType(), targetClassifier, targetFile);
		}
	}

	/**
	 * This method verifies if the custom parameter selectedSource and selectedTargetDirectory
	 * were set on the command line
	 * @param sourcePath source path of the file to process
	 * @param targetPath target path for the file to process
	 * @return
	 */
	private List<String> processCustomParameters(String sourcePath, String targetPath){
		Properties properties = System.getProperties();
		final List<String> args = new ArrayList<>();
		selectedSource = properties.getProperty(SELECTED_SOURCE);
		if (selectedSource != null && !selectedSource.isEmpty()) {
			log.info("setting custom source file:" + selectedSource);
			args.add(selectedSource);
			selectedTarget = properties.getProperty(SELECTED_TARGET);
			if (selectedTarget != null && !selectedTarget.isEmpty()) {
				log.info("setting custom target file:" + selectedTarget);
				if(!Files.isDirectory(Paths.get(selectedSource)) && Files.isDirectory(Paths.get(selectedTarget))) {
					String fileName = Paths.get(selectedSource).getFileName().toString();
					String newFileName = new StringBuilder().append(selectedTarget)
						.append(FileSystems.getDefault().getSeparator())
						.append(OUTPUT_PREFIX).append(fileName).toString();
					args.add(newFileName);
				} else {
					args.add(selectedTarget);
				}
			}
			if (this.overwrite) {
				args.add("-o");
			}
			if (this.invert) {
				args.add("-i");
			}
		} else {
			args.add(sourcePath);
			args.add(targetPath);
			if (this.overwrite) {
				args.add("-o");
			}
			if (this.invert) {
				args.add("-i");
			}

		}
		return args;
	}

	public void transform(final Transformer transformer, final File source) throws MojoFailureException {
		final String targetClassifier = this.classifier;
		final File targetDirectory = new File(source + "-" + targetClassifier);
		//processing new parameters to select source file and target directory to execute transform operation
		// from maven command line
		log.info("Processing custom parameters for source and target file");
		List<String> args = processCustomParameters(source.getAbsolutePath(), targetDirectory.getAbsolutePath());

		transformer.setArgs(args.toArray(new String[0]));
		int rc = transformer.run();

		if (TARGET_AS_ORIGIN.equals(classifier) && !isSourceAndTargetAvailable()) {
			try {
				if (source.isDirectory()) {
					FileUtils.deleteDirectory(source);
				} else {
					source.delete();
				}
				targetDirectory.renameTo(source);
			} catch (IOException ex) {
				throw new MojoFailureException("Transformer failed", ex);
			}
		}
		if (rc != 0) {
			throw new MojoFailureException("Transformer failed with an error: " + Transformer.RC_DESCRIPTIONS[rc]);
		}
	}
	private boolean isSourceAndTargetAvailable() {
		Properties properties = System.getProperties();
		String selectedSource = properties.getProperty(SELECTED_SOURCE);
		String selectedTarget = properties.getProperty(SELECTED_TARGET);
		return selectedSource != null && selectedTarget != null;
	}

	/**
	 * Builds a configured transformer for the specified source and target
	 * artifacts
	 *
	 * @return A configured transformer
	 */
	public Transformer getTransformer() {
		final Transformer transformer = new Transformer(System.out, System.err);
		transformer.setOptionDefaults(JakartaTransformer.class, getOptionDefaults());
		return transformer;
	}

	/**
	 * Gets the source artifacts that should be transformed
	 *
	 * @return an array to artifacts to be transformed
	 */
	public Artifact[] getSourceArtifacts() {
		List<Artifact> artifactList = new ArrayList<>();
		if (project.getArtifact() != null && project.getArtifact()
			.getFile() != null) {
			artifactList.add(project.getArtifact());
		}

		for (final Artifact attachedArtifact : project.getAttachedArtifacts()) {
			if (attachedArtifact.getFile() != null) {
				artifactList.add(attachedArtifact);
			}
		}

		return artifactList.toArray(new Artifact[0]);
	}

        public File getTestDirectory() {
            return new File(project.getBuild().getTestOutputDirectory());
        }

	private Map<Transformer.AppOption, String> getOptionDefaults() {
		Map<Transformer.AppOption, String> optionDefaults = new HashMap<>();
		optionDefaults.put(Transformer.AppOption.RULES_RENAMES,
			isEmpty(rulesRenamesUri) ? "jakarta-renames.properties" : rulesRenamesUri);
		optionDefaults.put(Transformer.AppOption.RULES_VERSIONS,
			isEmpty(rulesVersionUri) ? "jakarta-versions.properties" : rulesVersionUri);
		optionDefaults.put(Transformer.AppOption.RULES_BUNDLES,
			isEmpty(rulesBundlesUri) ? "jakarta-bundles.properties" : rulesBundlesUri);
		optionDefaults.put(Transformer.AppOption.RULES_DIRECT,
			isEmpty(rulesDirectUri) ? "jakarta-direct.properties" : rulesDirectUri);
		optionDefaults.put(Transformer.AppOption.RULES_MASTER_TEXT,
			isEmpty(rulesXmlsUri) ? "jakarta-text-master.properties" : rulesXmlsUri);
		optionDefaults.put(Transformer.AppOption.RULES_PER_CLASS_CONSTANT,
			isEmpty(rulesPerClassConstantUri) ? "jakarta-per-class-constant-master.properties" : rulesPerClassConstantUri);
		return optionDefaults;
	}

	private boolean isEmpty(final String input) {
		return input == null || input.trim()
			.length() == 0;
	}

	void setProject(MavenProject project) {
		this.project = project;
	}

	void setClassifier(String classifier) {
		this.classifier = classifier;
	}

	MavenProjectHelper getProjectHelper() {
		return projectHelper;
	}

	void setProjectHelper(MavenProjectHelper projectHelper) {
		this.projectHelper = projectHelper;
	}

	void setOverwrite(Boolean overwrite) {
		this.overwrite = overwrite;
	}

	public void setInvert(Boolean invert) {
		this.invert = invert;
	}

	void setOutputDirectory(File outputDirectory) {
		this.outputDirectory = outputDirectory;
	}

	public void setSelectedSource(String selectedSource) {
		this.selectedSource = selectedSource;
	}

	public void setSelectedTarget(String selectedTarget) {
		this.selectedTarget = selectedTarget;
	}

	public String getSelectedSource() {
		return selectedSource;
	}

	public String getSelectedTarget() {
		return selectedTarget;
	}

	public void setMainSource(Boolean mainSource) {
		this.mainSource = mainSource;
	}

	public Boolean getMainSource() {
		return mainSource;
	}

	public Boolean getTestSource() {
		return testSource;
	}

	public void setTestSource(Boolean testSource) {
		this.testSource = testSource;
	}
}
