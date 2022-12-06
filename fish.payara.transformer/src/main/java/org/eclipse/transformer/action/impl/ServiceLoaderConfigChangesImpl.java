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

package org.eclipse.transformer.action.impl;

import java.io.PrintStream;
import java.util.logging.Level;

import java.util.logging.Logger;

public class ServiceLoaderConfigChangesImpl extends ChangesImpl {

	public ServiceLoaderConfigChangesImpl() {
		super();

		this.clearChanges();
	}

	//

	@Override
	public boolean hasNonResourceNameChanges() {
		return (changedProviders > 0);
	}

	@Override
	public void clearChanges() {
		changedProviders = 0;
		unchangedProviders = 0;

		super.clearChanges();
	}

	//

	private int	changedProviders;
	private int	unchangedProviders;

	public void addChangedProvider() {
		changedProviders++;
	}

	public int getChangedProviders() {
		return changedProviders;
	}

	public void addUnchangedProvider() {
		unchangedProviders++;
	}

	public int getUnchangedProviders() {
		return unchangedProviders;
	}

	//

	@Override
	public void displayVerbose(PrintStream printStream, String inputPath, String outputPath) {
		printStream.printf("Input  [ %s ] as [ %s ]\n", getInputResourceName(), inputPath);
		printStream.printf("Output [ %s ] as [ %s ]\n", getOutputResourceName(), outputPath);
		printStream.printf("Replacements [ %s ]\n", getChangedProviders());
	}

	@Override
	public void displayVerbose(Logger logger, String inputPath, String outputPath) {
		if (!logger.isLoggable(Level.INFO)) {
			return;
		}

                logger.log(Level.INFO, "Input  [ {} ] as [ {} ]", new Object[]{getInputResourceName(), inputPath});
                logger.log(Level.INFO, "Output [ {} ] as [ {} ]", new Object[]{getOutputResourceName(), outputPath});
                logger.log(Level.INFO, "Replacements [ {} ]", new Object[]{getChangedProviders()});
	}

}
