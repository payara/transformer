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

package org.eclipse.transformer.action.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.transformer.TransformException;
import org.eclipse.transformer.action.Action;
import org.eclipse.transformer.action.BundleData;
import org.eclipse.transformer.action.SignatureRule.SignatureType;
import org.eclipse.transformer.util.ByteData;
import org.eclipse.transformer.util.FileUtils;
import org.eclipse.transformer.util.InputStreamData;
import java.util.logging.Logger;

import aQute.bnd.signatures.ArrayTypeSignature;
import aQute.bnd.signatures.ClassSignature;
import aQute.bnd.signatures.ClassTypeSignature;
import aQute.bnd.signatures.FieldSignature;
import aQute.bnd.signatures.JavaTypeSignature;
import aQute.bnd.signatures.MethodSignature;
import aQute.bnd.signatures.ReferenceTypeSignature;
import aQute.bnd.signatures.Result;
import aQute.bnd.signatures.SimpleClassTypeSignature;
import aQute.bnd.signatures.ThrowsSignature;
import aQute.bnd.signatures.TypeArgument;
import aQute.bnd.signatures.TypeParameter;
import aQute.lib.io.IO;
import java.util.logging.Level;

public abstract class ActionImpl implements Action {
	public ActionImpl(Logger logger, boolean isTerse, boolean isVerbose, InputBufferImpl buffer,
		SelectionRuleImpl selectionRule, SignatureRuleImpl signatureRule) {

		this.logger = logger;
		this.isTerse = isTerse;
		this.isVerbose = isVerbose;

		this.buffer = buffer;

		this.selectionRule = selectionRule;
		this.signatureRule = signatureRule;

		this.changes = new ArrayList<>();
		this.numActiveChanges = 0;
		this.activeChanges = null;
		this.lastActiveChanges = null;
	}

	//

	public interface ActionInit<A extends ActionImpl> {
		A apply(Logger logger, boolean isTerse, boolean isVerbose, InputBufferImpl buffer,
			SelectionRuleImpl selectionRule, SignatureRuleImpl signatureRule);
	}

	public <A extends ActionImpl> A createUsing(ActionInit<A> init) {
		return init.apply(getLogger(), getIsTerse(), getIsVerbose(), getBuffer(), getSelectionRule(),
			getSignatureRule());
	}

	//

	private final Logger	logger;
	private final boolean	isTerse;
	private final boolean	isVerbose;

	public Logger getLogger() {
		return logger;
	}

	public boolean getIsTerse() {
		return isTerse;
	}

	public boolean getIsVerbose() {
		return isVerbose;
	}

	public void trace(String message, Object... parms) {
		getLogger().log(Level.FINEST, message, parms);
	}

	public void debug(String message, Object... parms) {
		getLogger().log(Level.FINE, message, parms);
	}

	public void info(String message, Object... parms) {
		getLogger().log(Level.INFO, message, parms);
	}

	public void terse(String message, Object... parms) {
		if (getIsTerse()) {
			info(message, parms);
		}
	}

	public void verbose(String message, Object... parms) {
		if (getIsVerbose()) {
			info(message, parms);
		}
	}

	public void warn(String message, Object... parms) {
		getLogger().log(Level.WARNING, message, parms);
	}

	public void error(String message, Object... parms) {
		getLogger().log(Level.SEVERE, message, parms);
	}

	public void error(String message, Throwable th, Object... parms) {
		Logger useLogger = getLogger();
		if (!useLogger.isLoggable(Level.SEVERE)) {
			return;
		}

		if (parms.length != 0) {
			message = message.replace("{}", "%s");
			message = String.format(message, parms);
		}

		useLogger.log(Level.SEVERE, message, th);
	}

	//

	private final InputBufferImpl buffer;

	@Override
	public InputBufferImpl getBuffer() {
		return buffer;
	}

	@Override
	public byte[] getInputBuffer() {
		return getBuffer().getInputBuffer();
	}

	@Override
	public void setInputBuffer(byte[] inputBuffer) {
		getBuffer().setInputBuffer(inputBuffer);
	}

	//

	private final SelectionRuleImpl selectionRule;

	public SelectionRuleImpl getSelectionRule() {
		return selectionRule;
	}

	@Override
	public boolean select(String resourceName) {
		return getSelectionRule().select(resourceName);
	}

	public boolean selectIncluded(String resourceName) {
		return getSelectionRule().selectIncluded(resourceName);
	}

	public boolean rejectExcluded(String resourceName) {
		return getSelectionRule().rejectExcluded(resourceName);
	}

	//

	protected final SignatureRuleImpl signatureRule;

	@Override
	public SignatureRuleImpl getSignatureRule() {
		return signatureRule;
	}

	public BundleData getBundleUpdate(String symbolicName) {
		return getSignatureRule().getBundleUpdate(symbolicName);
	}

	public Map<String, String> getPackageRenames() {
		return getSignatureRule().getPackageRenames();
	}

	public Map<String, String> getPackageVersions() {
		return getSignatureRule().getPackageVersions();
	}

	public String replacePackage(String initialName) {
		return getSignatureRule().replacePackage(initialName);
	}

	public String replaceBinaryPackage(String initialName) {
		return getSignatureRule().replaceBinaryPackage(initialName);
	}

	public String replaceEmbeddedPackages(String embeddingText) {
		return getSignatureRule().replacePackages(embeddingText);
	}

	public String replaceText(String inputFileName, String text) {
		return getSignatureRule().replaceText(inputFileName, text);
	}

	public String transformConstantAsBinaryType(String inputConstant) {
		return getSignatureRule().transformConstantAsBinaryType(inputConstant);
	}

	public String transformConstantAsBinaryType(String inputConstant, boolean simpleSubstitution) {
		return getSignatureRule().transformConstantAsBinaryType(inputConstant, simpleSubstitution);
	}

	public String transformBinaryType(String inputName) {
		return getSignatureRule().transformBinaryType(inputName);
	}

	public String transformConstantAsDescriptor(String inputConstant) {
		return getSignatureRule().transformConstantAsDescriptor(inputConstant);
	}

	public String transformConstantAsDescriptor(String inputConstant, boolean simpleSubstitution) {
		return getSignatureRule().transformConstantAsDescriptor(inputConstant, simpleSubstitution);
	}

	public String transformDescriptor(String inputDescriptor) {
		return getSignatureRule().transformDescriptor(inputDescriptor);
	}

	public String transform(String input, SignatureType signatureType) {
		return getSignatureRule().transform(input, signatureType);
	}

	public ClassSignature transform(ClassSignature classSignature) {
		return getSignatureRule().transform(classSignature);
	}

	public FieldSignature transform(FieldSignature fieldSignature) {
		return getSignatureRule().transform(fieldSignature);
	}

	public MethodSignature transform(MethodSignature methodSignature) {
		return getSignatureRule().transform(methodSignature);
	}

	public Result transform(Result type) {
		return getSignatureRule().transform(type);
	}

	public ThrowsSignature transform(ThrowsSignature type) {
		return getSignatureRule().transform(type);
	}

	public ArrayTypeSignature transform(ArrayTypeSignature inputType) {
		return getSignatureRule().transform(inputType);
	}

	public TypeParameter transform(TypeParameter inputTypeParameter) {
		return getSignatureRule().transform(inputTypeParameter);
	}

	public ClassTypeSignature transform(ClassTypeSignature inputType) {
		return getSignatureRule().transform(inputType);
	}

	public SimpleClassTypeSignature transform(SimpleClassTypeSignature inputSignature) {
		return getSignatureRule().transform(inputSignature);
	}

	public TypeArgument transform(TypeArgument inputArgument) {
		return getSignatureRule().transform(inputArgument);
	}

	public JavaTypeSignature transform(JavaTypeSignature type) {
		return getSignatureRule().transform(type);
	}

	public ReferenceTypeSignature transform(ReferenceTypeSignature type) {
		return getSignatureRule().transform(type);
	}

	public String transformDirectString(String initialValue) {
		return getSignatureRule().getDirectString(initialValue);
	}

	public String transformConstantString(String initialValue, String className) {
		return getSignatureRule().getConstantString(initialValue, className);
	}

	//

	public abstract String getAcceptExtension();

	@Override
	public boolean accept(String resourceName) {
		return accept(resourceName, null);
	}

	@Override
	public boolean accept(String resourceName, File resourceFile) {
		return resourceName.toLowerCase()
			.endsWith(getAcceptExtension());
	}

	//

	protected ChangesImpl newChanges() {
		return new ChangesImpl();
	}

	protected final List<ChangesImpl>	changes;
	protected int						numActiveChanges;
	protected ChangesImpl				activeChanges;
	protected ChangesImpl				lastActiveChanges;

	protected void startRecording(String inputName) {
		if (getIsVerbose()) {
			info("Start processing [ {} ] using [ {} ]", inputName, getActionType());
		}

		if (numActiveChanges == changes.size()) {
			changes.add(activeChanges = newChanges());
		} else {
			activeChanges = changes.get(numActiveChanges);
			activeChanges.clearChanges();
		}
		numActiveChanges++;
	}

	protected void stopRecording(String inputName) {
		if (getIsVerbose()) {
			String changeText;

			boolean nameChanged = activeChanges.hasResourceNameChange();
			boolean contentChanged = activeChanges.hasNonResourceNameChanges();

			if (nameChanged && contentChanged) {
				changeText = "Name and content changes";
			} else if (nameChanged) {
				changeText = "Name changes";
			} else if (contentChanged) {
				changeText = "Content changes";
			} else {
				changeText = "No changes";
			}

			info("Stop processing [ {} ] using [ {} ]: {}", inputName, getActionType(), changeText);
		}

		lastActiveChanges = activeChanges;

		numActiveChanges--;
		if (numActiveChanges == 0) {
			activeChanges = null;
		} else {
			activeChanges = changes.get(numActiveChanges);
		}
	}

	//

	@Override
	public ChangesImpl getActiveChanges() {
		return activeChanges;
	}

	protected void setResourceNames(String inputResourceName, String outputResourceName) {
		ChangesImpl useChanges = getActiveChanges();
		useChanges.setInputResourceName(inputResourceName);
		useChanges.setOutputResourceName(outputResourceName);
	}

	@Override
	public void addReplacement() {
		getActiveChanges().addReplacement();
	}

	@Override
	public void addReplacements(int additions) {
		getActiveChanges().addReplacements(additions);
	}

	//

	@Override
	public boolean hasChanges() {
		return getActiveChanges().hasChanges();
	}

	@Override
	public boolean hasResourceNameChange() {
		return getActiveChanges().hasResourceNameChange();
	}

	@Override
	public boolean hasNonResourceNameChanges() {
		return getActiveChanges().hasNonResourceNameChanges();
	}

	//

	@Override
	public ChangesImpl getLastActiveChanges() {
		return lastActiveChanges;
	}

	@Override
	public boolean hadChanges() {
		return getLastActiveChanges().hasChanges();
	}

	@Override
	public boolean hadResourceNameChange() {
		return getLastActiveChanges().hasResourceNameChange();
	}

	@Override
	public boolean hadNonResourceNameChanges() {
		return getLastActiveChanges().hasNonResourceNameChanges();
	}

	//

	@Override
	public boolean useStreams() {
		return false;
	}

	/**
	 * Read bytes from an input stream. Answer byte data and a count of bytes
	 * read.
	 *
	 * @param inputName The name of the input stream.
	 * @param inputStream A stream to be read.
	 * @param inputCount The count of bytes to read from the stream. {@code -1}
	 *            if the count of input bytes is not known.
	 * @return Byte data from the read.
	 * @throws TransformException Indicates a read failure.
	 */
	protected ByteData read(String inputName, InputStream inputStream, int inputCount) throws TransformException {
		byte[] readBytes = getInputBuffer();

		ByteData readData;
		try {
			readData = FileUtils.read(inputName, inputStream, readBytes, inputCount); // throws
																						// IOException
		} catch (IOException e) {
			throw new TransformException("Failed to read raw bytes [ " + inputName + " ] count [ " + inputCount + " ]",
				e);
		}

		setInputBuffer(readData.data);

		return readData;
	}

	/**
	 * Write data to an output stream. Convert any exception thrown when
	 * attempting the write into a {@link TransformException}.
	 *
	 * @param outputData Data to be written.
	 * @param outputStream Stream to which to write the data.
	 * @throws TransformException Thrown in case of a write failure.
	 */
	protected void write(ByteData outputData, OutputStream outputStream) throws TransformException {
		try {
			outputStream.write(outputData.data, outputData.offset, outputData.length); // throws
																						// IOException

		} catch (IOException e) {
			throw new TransformException("Failed to write [ " + outputData.name + " ]" + " at [ " + outputData.offset
				+ " ]" + " count [ " + outputData.length + " ]", e);
		}
	}

	//

	@Override
	public InputStreamData apply(String inputName, InputStream inputStream) throws TransformException {

		return apply(inputName, inputStream, InputStreamData.UNKNOWN_LENGTH); // throws
																				// JakartaTransformException
	}

	@Override
	public InputStreamData apply(String inputName, InputStream inputStream, int inputCount) throws TransformException {

		startRecording(inputName);
		try {
			return basicApply(inputName, inputStream, inputCount); // throws
																	// TransformException
																	// {
		} finally {
			stopRecording(inputName);
		}
	}

	public InputStreamData basicApply(String inputName, InputStream inputStream, int inputCount)
		throws TransformException {

		String className = getClass().getSimpleName();
		String methodName = "apply";

		debug("[ {}.{} ]: Requested [ {} ] [ {} ]", className, methodName, inputName, inputCount);
		ByteData inputData = read(inputName, inputStream, inputCount); // throws
																		// JakartaTransformException
		debug("[ {}.{} ]: Obtained [ {} ] [ {} ] [ {} ]", className, methodName, inputName, inputData.length,
			inputData.data);

		ByteData outputData;
		try {
			outputData = apply(inputName, inputData.data, inputData.length);
			// throws JakartaTransformException
		} catch (Throwable th) {
			error("Transform failure [ {} ]", th, inputName);
			outputData = null;
		}

		if (outputData == null) {
			debug("[ {}.{} ]: Null transform", className, methodName);
			outputData = inputData;
		} else {
			debug("[ {}.{} ]: Active transform [ {} ] [ {} ] [ {} ]", className, methodName, outputData.name,
				outputData.length, outputData.data);
		}

		return new InputStreamData(outputData);
	}

	@Override
	public void apply(String inputName, InputStream inputStream, long inputCount, OutputStream outputStream)
		throws TransformException {

		startRecording(inputName);
		try {
			basicApply(inputName, inputStream, inputCount, outputStream); // throws
																			// TransformException
		} finally {
			stopRecording(inputName);
		}
	}

	public void basicApply(String inputName, InputStream inputStream, long inputCount, OutputStream outputStream)
		throws TransformException {

		int intInputCount = FileUtils.verifyArray(0, inputCount);

		String className = getClass().getSimpleName();
		String methodName = "apply";

		debug("[ {}.{} ]: Requested [ {} ] [ {} ]", className, methodName, inputName, inputCount);
		ByteData inputData = read(inputName, inputStream, intInputCount); // throws
																			// JakartaTransformException
		debug("[ {}.{} ]: Obtained [ {} ] [ {} ]", className, methodName, inputName, inputData.length);

		ByteData outputData;
		try {
			outputData = apply(inputName, inputData.data, inputData.length);
			// throws JakartaTransformException
		} catch (Throwable th) {
			error("Transform failure [ {} ]", th, inputName);
			outputData = null;
		}

		if (outputData == null) {
			debug("[ {}.{} ]: Null transform", className, methodName);
			outputData = inputData;
		} else {
			debug("[ {}.{} ]: Active transform [ {} ] [ {} ]", className, methodName, outputData.name,
				outputData.length);
		}

		write(outputData, outputStream); // throws JakartaTransformException
	}

	protected abstract ByteData apply(String inputName, byte[] inputBytes, int inputLength) throws TransformException;

	@Override
	public void apply(String inputName, File inputFile, File outputFile) throws TransformException {

		long inputLength = inputFile.length();
		debug("Input [ {} ] Length [ {} ]", inputName, inputLength);

		InputStream inputStream = openInputStream(inputFile);
		Path temporalFile = null;
		boolean isSameName = false;
		try {
			OutputStream outputStream = null;

			if(inputFile.getAbsolutePath().equals(outputFile.getAbsolutePath())) {
				Path pathFromInput = Paths.get(inputFile.getAbsolutePath());
				temporalFile = Files.createTempFile(pathFromInput.getParent(),null,null);
				outputStream = openOutputStream(temporalFile.toFile());
				isSameName = true;
			} else {
				outputStream = openOutputStream(outputFile);
			}
			try {
				apply(inputName, inputStream, inputLength, outputStream);
			} finally {
				closeOutputStream(outputFile, outputStream);
			}
		} catch (IOException e) {
			String message = String.format("Error when processing File: %s", e.getMessage());
			logger.log(Level.SEVERE, message);
			throw new TransformException(message, e);
		} finally {
			closeInputStream(inputFile, inputStream);
		}
		if(inputFile.exists() && isSameName) {
			inputFile.delete();
		}
		if(temporalFile != null && temporalFile.toFile().exists()) {
			Path pathFromInput = Paths.get(inputFile.getAbsolutePath());
			try {
				Files.move(temporalFile, pathFromInput);
			} catch (IOException e) {
				String message = String.format("Error when processing File: %s", e.getMessage());
				logger.log(Level.SEVERE, message);
				throw new TransformException(message, e);
			}
		}
	}
	protected InputStream openInputStream(File inputFile) throws TransformException {

		try {
			return IO.stream(inputFile);
		} catch (IOException e) {
			throw new TransformException("Failed to open input [ " + inputFile.getAbsolutePath() + " ]", e);
		}
	}

	protected void closeInputStream(File inputFile, InputStream inputStream) throws TransformException {

		try {
			inputStream.close();
		} catch (IOException e) {
			throw new TransformException("Failed to close input [ " + inputFile.getAbsolutePath() + " ]", e);
		}
	}

	private OutputStream openOutputStream(File outputFile) throws TransformException {

		try {
			return IO.outputStream(outputFile);
		} catch (IOException e) {
			throw new TransformException("Failed to open output [ " + outputFile.getAbsolutePath() + " ]", e);
		}
	}

	private void closeOutputStream(File outputFile, OutputStream outputStream) throws TransformException {

		try {
			outputStream.close();
		} catch (IOException e) {
			throw new TransformException("Failed to close output [ " + outputFile.getAbsolutePath() + " ]", e);
		}
	}
}
