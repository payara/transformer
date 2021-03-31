/** ******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: (EPL-2.0 OR Apache-2.0)
 ******************************************************************************* */
package org.eclipse.transformer.action.impl;

import java.io.File;
import org.eclipse.transformer.action.ActionType;
import java.util.logging.Logger;

public class TagActionImpl extends TextActionImpl {

    public TagActionImpl(Logger logger, boolean isTerse, boolean isVerbose, InputBufferImpl buffer,
            SelectionRuleImpl selectionRule, SignatureRuleImpl signatureRule) {

        super(logger, isTerse, isVerbose, buffer, selectionRule, signatureRule);
    }

    @Override
    public String getName() {
        return "Tag Action";
    }

    @Override
    public ActionType getActionType() {
        return ActionType.TAG;
    }

    @Override
    public String getAcceptExtension() {
        return ".tag";
    }

    @Override
    public boolean accept(String resourceName, File resourceFile) {
        return resourceName.toLowerCase()
                .endsWith(getAcceptExtension());
    }

}
