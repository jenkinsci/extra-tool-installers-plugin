/*
 * The MIT License
 *
 * Copyright 2013 Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.synopsys.arc.jenkinsci.plugins.extratoolinstallers.installers;

import static com.synopsys.arc.jenkinsci.plugins.extratoolinstallers.utils.EnvStringParseHelper.checkStringForMacro;
import static com.synopsys.arc.jenkinsci.plugins.extratoolinstallers.utils.EnvStringParseHelper.substituteNodeVariables;
import com.synopsys.arc.jenkinsci.plugins.extratoolinstallers.utils.ExtraToolInstallersException;
import hudson.model.Node;
import hudson.tools.ToolInstaller;

/**
 * Abstract class, which encapsulates common methods for installers.
 * @author Oleg Nenashev
 * @since 0.2.1
 */
public abstract class AbstractExtraToolInstaller extends ToolInstaller {
    private boolean failOnSubstitution;
    private final String toolHome;
    
    public AbstractExtraToolInstaller(String label, String toolHome, boolean failOnSubstitution) {
        super(label);
        this.failOnSubstitution = failOnSubstitution;
        this.toolHome = toolHome;
    }

    public final boolean isFailOnSubstitution() {
        return failOnSubstitution;
    }

    public final String getToolHome() {
        return toolHome;
    }
    
    /**
     * Substitute variables and fail on errors.
     * @param stringName Name of the field
     * @param macroString String to be checked
     * @param node Node, where tool is being installed
     * @return Substituted string
     * @throws ExtraToolInstallersException Substitution error
     */
    protected String substituteNodeVariablesValidated(String stringName, String macroString, Node node) 
            throws ExtraToolInstallersException {
        String res = substituteNodeVariables(macroString, node);
        if (isFailOnSubstitution()) {
            checkStringForMacro(this, stringName, res);
        }
        return res;
    }
}
