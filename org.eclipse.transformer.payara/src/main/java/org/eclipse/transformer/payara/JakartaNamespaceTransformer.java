/** ******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: (EPL-2.0 OR Apache-2.0)
 ******************************************************************************* */
package org.eclipse.transformer.payara;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import org.apache.commons.cli.ParseException;
import org.eclipse.transformer.TransformException;
import org.eclipse.transformer.Transformer;
import static org.eclipse.transformer.Transformer.FILE_TYPE_ERROR_RC;
import static org.eclipse.transformer.Transformer.LOGGER_SETTINGS_ERROR_RC;
import static org.eclipse.transformer.Transformer.PARSE_ERROR_RC;
import static org.eclipse.transformer.Transformer.RULES_ERROR_RC;
import static org.eclipse.transformer.Transformer.SUCCESS_RC;
import static org.eclipse.transformer.Transformer.TRANSFORM_ERROR_RC;
import static org.eclipse.transformer.Transformer.TransformOptions.OUTPUT_PREFIX;
import org.eclipse.transformer.jakarta.JakartaTransformer;

/**
 *
 * @author gaurav.gupta@payara.fish
 */
public class JakartaNamespaceTransformer extends Transformer {

    private final PayaraTransformOptions options;

    private final File output;

    private final boolean invert;

    public JakartaNamespaceTransformer(PrintStream sysOut, PrintStream sysErr, File input, boolean invert) throws IOException {
        super(sysOut, sysErr);
        String prefix = invert ? "JAVAX-" : "JAKARTA-";
        if (input.isDirectory()) {
            output = Files.createTempDirectory(input.getParentFile().toPath(), prefix + input.getName()).toFile();
        } else {
            output = File.createTempFile(prefix, input.getName(), input.getParentFile());
        }
        options = new PayaraTransformOptions(input, output, invert);
        this.invert = invert;
    }

    @Override
    public int run() {
        try {
            setArgs(new String[]{
                "--" + Transformer.AppOption.INVERT.getLongTag(), String.valueOf(invert),
                "--" + Transformer.AppOption.OVERWRITE.getLongTag(), "true"
            });
            setParsedArgs();
        } catch (ParseException e) {
            errorPrint("Exception parsing command line arguments: %s", e);
            return PARSE_ERROR_RC;
        }
        try {
            options.setLogging();
        } catch (TransformException e) {
            errorPrint("Logger settings error: %s", e);
            return LOGGER_SETTINGS_ERROR_RC;
        }
        detectLogFile();
        setOptionDefaults(JakartaTransformer.class, JakartaTransformer.getOptionDefaults());
        boolean loadedRules;
        try {
            loadedRules = options.setRules();
        } catch (IOException | IllegalArgumentException | URISyntaxException e) {
            dual_error("Exception loading rules:", e);
            return RULES_ERROR_RC;
        }
        if (!loadedRules) {
            dual_error("Transformation rules cannot be used");
            return RULES_ERROR_RC;
        }

        if (!options.setInput()) {
            return TRANSFORM_ERROR_RC;
        }

        if (!options.setOutput()) {
            return TRANSFORM_ERROR_RC;
        }

        if (!options.acceptAction()) {
            dual_error("No action selected");
            return FILE_TYPE_ERROR_RC;
        }

        try {
            options.transform();
        } catch (TransformException e) {
            dual_error("Transform failure:", e);
            return TRANSFORM_ERROR_RC;
        } catch (Throwable th) {
            dual_error("Unexpected failure:", th);
            return TRANSFORM_ERROR_RC;
        }

        return SUCCESS_RC;
    }

    public File getOutput() {
        return output;
    }

    class PayaraTransformOptions extends TransformOptions {

        public PayaraTransformOptions(File input, File output, boolean invert) {
            this.inputFile = input;
            this.outputFile = output;
        }

        @Override
        public boolean setInput() {
            if (inputFile == null) {
                dual_error("No input file was specified");
                return false;
            }
            if (!inputFile.exists()) {
                dual_error("Input does not exist [ %s ] [ %s ]", inputName, inputPath);
                return false;
            }

            inputName = inputFile.getName();
            inputPath = inputFile.getAbsolutePath();

            dual_info("Input     [ %s ]", inputName);
            dual_info("          [ %s ]", inputPath);
            return true;
        }

        @Override
        public boolean setOutput() {

            boolean isExplicit = (outputFile != null);
            String useOutputName;
            if (isExplicit) {
                useOutputName = outputFile.getName();
            } else {
                int indexOfLastSlash = inputName.lastIndexOf('/');
                if (indexOfLastSlash == -1) {
                    useOutputName = OUTPUT_PREFIX + inputName;
                } else {
                    String inputPrefix = inputName.substring(0, indexOfLastSlash + 1);
                    String inputSuffix = inputName.substring(indexOfLastSlash + 1);
                    useOutputName = inputPrefix + OUTPUT_PREFIX + inputSuffix;
                }
            }

            String useOutputPath = outputFile.getAbsolutePath();

            boolean putIntoDirectory = (inputFile.isFile() && outputFile.isDirectory());

            if (putIntoDirectory) {
                useOutputName = useOutputName + '/' + inputName;
                if (isVerbose) {
                    dual_info("Output generated using input name and output directory [ %s ]", useOutputName);
                }

                outputFile = new File(useOutputName);
                useOutputPath = outputFile.getAbsolutePath();
            }

            String outputCase;
            if (isExplicit) {
                if (putIntoDirectory) {
                    outputCase = "Explicit directory";
                } else {
                    outputCase = "Explicit";
                }
            } else {
                if (putIntoDirectory) {
                    outputCase = "Directory generated from input";
                } else {
                    outputCase = "Generated from input";
                }
            }

            dual_info("Output    [ %s ] (%s)", useOutputName, outputCase);
            dual_info("          [ %s ]", useOutputPath);

            allowOverwrite = hasOption(AppOption.OVERWRITE);
            if (allowOverwrite) {
                dual_info("Overwrite of output is enabled");
            }

            if (outputFile.exists()) {
                if (allowOverwrite) {
                    dual_info("Output exists and will be overwritten [ %s ]", useOutputPath);
                } else {
                    dual_error("Output already exists [ %s ]", useOutputPath);
                    return false;
                }
            } else {
                if (allowOverwrite) {
                    if (isVerbose) {
                        dual_info("Overwritten specified, but output [ %s ] does not exist", useOutputPath);
                    }
                }
            }

            outputName = useOutputName;
            outputPath = useOutputPath;

            return true;
        }

    }

}
