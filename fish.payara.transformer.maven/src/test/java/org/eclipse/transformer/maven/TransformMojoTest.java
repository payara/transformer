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
// Copyright (c) 2022 Payara Foundation and/or its affiliates

package org.eclipse.transformer.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.stubs.DefaultArtifactHandlerStub;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.transformer.Transformer;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Rule;
import org.junit.Test;

public class TransformMojoTest extends AbstractMojoTestCase {

	@Rule
	public MojoRule rule = new MojoRule();

	@Override
	protected void setUp() throws Exception {
		super.setUp();
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		cleanGeneratedFiles();
		cleanSystemProperties();
		deleteProcessDirectory();
	}

	public void cleanSystemProperties() {
		String selectedSourceProperty = System.getProperty("selectedSource");
		String selectedTargetProperty = System.getProperty("selectedTarget");
		if(selectedSourceProperty != null) {
			System.clearProperty("selectedSource");
		}

		if(selectedTargetProperty != null) {
			System.clearProperty("selectedTarget");
		}
	}

	public void cleanGeneratedFiles() throws IOException {
		File fileResourceTransformed = getTestFile("src/test/resources/HelloResourceTransformed.java");
		File fileOutOutName = getTestFile("src/test/resources/output_HelloResource.java");
		Path deleteDirectory = Paths.get(getTestFile("src/test/resources/transformedFiles").getAbsolutePath());

		if (fileResourceTransformed.exists() && fileResourceTransformed.isFile()) {
			fileResourceTransformed.delete();
		}

		if (fileOutOutName.exists() && fileOutOutName.isFile()) {
			fileOutOutName.delete();
		}

		if (Files.exists(deleteDirectory)) {
			if(Files.isDirectory(deleteDirectory)) {
				Files.walk(deleteDirectory).map(Path::toFile).forEach(File::delete);
			}
			Files.delete(deleteDirectory);
		}
	}

	@Test
	public void testProjectArtifactTransformerPlugin() throws Exception {
		final TransformMojo mojo = new TransformMojo();
		mojo.setProjectHelper(this.rule.lookup(MavenProjectHelper.class));
		mojo.setOverwrite(true);
		mojo.setOutputDirectory(new File("target"));

		assertNotNull(mojo);

		final File targetDirectory = getTestFile("src/test/projects/transform-build-artifact");
		final File modelDirectory = new File(targetDirectory, "target/model");
		final File pom = new File(targetDirectory, "pom.xml");

		final MavenProject mavenProject = createMavenProject(modelDirectory, pom, "war", "rest-sample");
		mavenProject.getArtifact().setFile(createService());
		mojo.setProject(mavenProject);
		mojo.setClassifier("transformed");

		final Artifact[] sourceArtifacts = mojo.getSourceArtifacts();
		assertEquals(1, sourceArtifacts.length);
		assertEquals("org.superbiz.rest", sourceArtifacts[0].getGroupId());
		assertEquals("rest-sample", sourceArtifacts[0].getArtifactId());
		assertEquals("1.0-SNAPSHOT", sourceArtifacts[0].getVersion());
		assertEquals("war", sourceArtifacts[0].getType());
		assertNull(sourceArtifacts[0].getClassifier());

		final Transformer transformer = mojo.getTransformer();
		assertNotNull(transformer);
		mojo.setInvert(false);
		mojo.transform(transformer, sourceArtifacts[0]);
	}

	@Test
	public void testMultipleArtifactTransformerPlugin() throws Exception {
		final TransformMojo mojo = new TransformMojo();
		mojo.setOverwrite(true);
		mojo.setProjectHelper(this.rule.lookup(MavenProjectHelper.class));

		assertNotNull(mojo);

		final File targetDirectory = getTestFile("src/test/projects/transform-build-artifact");
		final File modelDirectory = new File(targetDirectory, "target/model");
		final File pom = new File(targetDirectory, "pom.xml");

		final MavenProject mavenProject = createMavenProject(modelDirectory, pom, "pom", "simple-service");

		mojo.setProject(mavenProject);
		mojo.setClassifier("transformed");
		mojo.setOutputDirectory(new File("target"));

		mojo.getProjectHelper()
			.attachArtifact(mavenProject, "zip", "test1", createService());
		mojo.getProjectHelper()
			.attachArtifact(mavenProject, "zip", "test2", createService());
		mojo.getProjectHelper()
			.attachArtifact(mavenProject, "zip", "test3", createService());

		final Artifact[] sourceArtifacts = mojo.getSourceArtifacts();
		assertEquals(4, sourceArtifacts.length);

		for (int i = 1; i < 4; i++) {
			assertEquals("org.superbiz.rest", sourceArtifacts[i].getGroupId());
			assertEquals("simple-service", sourceArtifacts[i].getArtifactId());
			assertEquals("1.0-SNAPSHOT", sourceArtifacts[i].getVersion());
			assertEquals("zip", sourceArtifacts[i].getType());
			assertEquals("test" + (i), sourceArtifacts[i].getClassifier());
		}

		final Transformer transformer = mojo.getTransformer();
		assertNotNull(transformer);
		mojo.setInvert(false);
		for (int i = 1; i < 4; i++) {
			mojo.transform(transformer, sourceArtifacts[i]);
		}

		assertEquals(3, mavenProject.getAttachedArtifacts()
			.size());
		Set<String> classifiers = mavenProject.getAttachedArtifacts()
			.stream()
			.filter(a -> (a.getType()
				.equals("zip")
				&& a.getArtifactId()
				.equals("simple-service")))
			.map(a -> a.getClassifier())
			.collect(Collectors.toSet());

		assertEquals(3, mavenProject.getAttachedArtifacts()
			.size());
		assertTrue(classifiers.contains("test1"));
		assertTrue(classifiers.contains("test2"));
		assertTrue(classifiers.contains("test3"));
	}

	@Test
	public void testSetSelectedSourceFileAndTargetFileDestination() throws Exception {
		TransformMojo mojo = new TransformMojo();
		File pom = getTestFile("src/test/projects/transform-build-artifact/pom.xml");

		assertNotNull(pom);
		assertTrue(pom.exists());

		MavenProject mavenProject = createMavenProject(pom, "pom", "simple-service");
		Build build = createBuild();
		mavenProject.setBuild(build);
		MavenProjectHelper mavenProjectHelper = new ProjectHelper();
		mojo.setProjectHelper(mavenProjectHelper);
		mojo.setProject(mavenProject);
		mojo.setClassifier("transformed");
		System.setProperty("selectedSource", getTestFile("src/test/resources/HelloResource.java").getAbsolutePath());
		System.setProperty("selectedTarget", getTestFile("src/test/resources/HelloResourceTransformed.java").getAbsolutePath());
		Transformer transformer = mojo.getTransformer();
		String[] args = {mojo.getSelectedSource(), mojo.getSelectedTarget()};
		transformer.setArgs(args);
		mojo.setMainSource(false);
		mojo.setTestSource(true);
		mojo.setInvert(false);
		mojo.setOverwrite(true);
		assertNotNull(transformer);
		mojo.execute();
	}

	@Test
	public void testSetSelectedSourceFile() throws Exception {
		TransformMojo mojo = new TransformMojo();
		File pom = getTestFile("src/test/projects/transform-build-artifact/pom.xml");

		assertNotNull(pom);
		assertTrue(pom.exists());

		MavenProject mavenProject = createMavenProject(pom, "pom", "simple-service");
		mojo.setProject(mavenProject);
		mojo.setClassifier("transformed");
		System.setProperty("selectedSource", getTestFile("src/test/resources/HelloResource.java").getAbsolutePath());
		Transformer transformer = mojo.getTransformer();
		String[] args = {mojo.getSelectedSource(), mojo.getSelectedTarget()};
		transformer.setArgs(args);
		mojo.setMainSource(true);
		mojo.setTestSource(false);
		mojo.setInvert(false);
		mojo.setOverwrite(true);
		assertNotNull(transformer);
		mojo.execute();
	}

	@Test
	public void testSetSelectedSourceDirectoryToNewDirectory() throws Exception {
		TransformMojo mojo = new TransformMojo();
		File pom = getTestFile("src/test/projects/transform-build-artifact/pom.xml");

		assertNotNull(pom);
		assertTrue(pom.exists());

		MavenProject mavenProject = createMavenProject(pom, "pom", "simple-service");
		Build build = createBuild();
		mavenProject.setBuild(build);
		MavenProjectHelper mavenProjectHelper = new ProjectHelper();
		mojo.setProjectHelper(mavenProjectHelper);
		mojo.setProject(mavenProject);
		mojo.setClassifier("transformed");
		System.setProperty("selectedSource", getTestFile("src/test/resources/sourceFiles").getAbsolutePath());
		System.setProperty("selectedTarget", getTestFile("src/test/resources/transformedFiles").getAbsolutePath());
		Transformer transformer = mojo.getTransformer();
		String[] args = {mojo.getSelectedSource(), mojo.getSelectedTarget()};
		transformer.setArgs(args);
		mojo.setMainSource(false);
		mojo.setTestSource(true);
		mojo.setInvert(false);
		mojo.setOverwrite(true);
		assertNotNull(transformer);
		mojo.execute();
	}

	@Test
	public void testSelectecSourceDirectoryToSameDirectory() throws Exception {
		TransformMojo mojo = new TransformMojo();
		File pom = getTestFile("src/test/projects/transform-build-artifact/pom.xml");

		assertNotNull(pom);
		assertTrue(pom.exists());

		MavenProject mavenProject = createMavenProject(pom, "pom", "simple-service");
		Build build = createBuild();
		mavenProject.setBuild(build);
		MavenProjectHelper mavenProjectHelper = new ProjectHelper();
		mojo.setProjectHelper(mavenProjectHelper);
		mojo.setProject(mavenProject);
		mojo.setClassifier("transformed");
		createProcessDirectory();
		System.setProperty("selectedSource", getTestFile("src/test/resources/processDirectory").getAbsolutePath());
		System.setProperty("selectedTarget", getTestFile("src/test/resources/processDirectory").getAbsolutePath());
		Transformer transformer = mojo.getTransformer();
		String[] args = {mojo.getSelectedSource(), mojo.getSelectedTarget()};
		transformer.setArgs(args);
		mojo.setMainSource(false);
		mojo.setTestSource(true);
		mojo.setInvert(false);
		mojo.setOverwrite(true);
		assertNotNull(transformer);
		mojo.execute();
	}

	public void createProcessDirectory() {
		String sourceFile = getTestFile("src/test/resources/sourceFiles/HelloResource.java").getAbsolutePath();
		Path path = Paths.get(sourceFile);
		Path basePath = path.getParent().getParent();
		//createDirectory
		Path newDirectory = Paths.get(basePath.toAbsolutePath().toString(),"processDirectory");
		Path targetFile = Paths.get(newDirectory.toAbsolutePath().toString(), "HelloResource.java");
		if(!Files.exists(newDirectory)) {
			try {
				Files.createDirectory(newDirectory);
				Files.copy(path, targetFile);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public void deleteProcessDirectory() {
		Path processedFile = Paths.get("src/test/resources/processDirectory/HelloResource.java");
		if(Files.exists(processedFile)) {
			try {
				Files.walk(processedFile.getParent()).map(Path::toFile).forEach(File::delete);
				Files.delete(processedFile.getParent());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}


	public MavenProject createMavenProject(final File pom, final String packaging,
										   final String artfifactId) {
		MavenProject mavenProject = new MavenProject();
		mavenProject.setFile(pom);
		mavenProject.setGroupId("org.superbiz.rest");
		mavenProject.setArtifactId(artfifactId);
		mavenProject.setVersion("1.0-SNAPSHOT");
		mavenProject.setPackaging(packaging);
		DefaultArtifact defaultArtifact = new DefaultArtifact(mavenProject.getGroupId(), mavenProject.getArtifactId(),
			mavenProject.getVersion(), (String) null, "war", (String) null,
			new DefaultArtifactHandlerStub(packaging, null));
		defaultArtifact.setFile(new File(getTestFile("/test/resources/target.war").getAbsolutePath()));
		mavenProject.setArtifact(defaultArtifact);
		return mavenProject;
	}

	public MavenProject createMavenProject(final File modelDirectory, final File pom, final String packaging,
										   final String artfifactId) {
		MavenProject mavenProject = createMavenProject(pom, packaging, artfifactId);
		if (modelDirectory != null) {
			mavenProject.getBuild()
				.setDirectory(modelDirectory.getParentFile()
					.getAbsolutePath());
			mavenProject.getBuild()
				.setOutputDirectory(modelDirectory.getAbsolutePath());
		}
		return mavenProject;
	}

	public Build createBuild() {
		Build build = new Build();
		build.setTestOutputDirectory("src/temp");
		build.setSourceDirectory("src/test");
		return build;
	}

	public File createService() throws IOException {
		final File tempFile = File.createTempFile("service", ".war");
		tempFile.delete();
		final WebArchive webArchive = ShrinkWrap.create(WebArchive.class, "service.war")
			.addClass(EchoService.class);
		webArchive.as(ZipExporter.class)
			.exportTo(tempFile, true);
		return tempFile;
	}

	class ProjectHelper implements MavenProjectHelper {

		@Override
		public void attachArtifact(MavenProject mavenProject, File file, String s) {

		}

		@Override
		public void attachArtifact(MavenProject mavenProject, String s, File file) {

		}

		@Override
		public void attachArtifact(MavenProject mavenProject, String s, String s1, File file) {

		}

		@Override
		public void addResource(MavenProject mavenProject, String s, List<String> list, List<String> list1) {

		}

		@Override
		public void addTestResource(MavenProject mavenProject, String s, List<String> list, List<String> list1) {

		}
	}
}
