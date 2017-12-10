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

import hudson.Extension;
import hudson.FilePath;
import hudson.RestrictedSince;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.CommandInstaller;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstallerDescriptor;
import hudson.util.FormValidation;
import java.io.IOException;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Installs tool from a shared directory.
 * Actually, this installer doesn't perform any actions.
 * @author Oleg Nenashev
 */
public class SharedDirectoryInstaller extends AbstractExtraToolInstaller {

    @DataBoundConstructor
    public SharedDirectoryInstaller(String label, String toolHome, boolean failOnSubstitution) {
        super(label, toolHome, failOnSubstitution);
    }

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log) throws IOException, InterruptedException {
        String substitutedHome = substituteNodeVariablesValidated("Tool Home", getToolHome(), node);   
        FilePath dir = preferredLocation(tool, node);
        return dir.child(substitutedHome);
    }
    
    @Extension
    public static class DescriptorImpl extends ToolInstallerDescriptor<CommandInstaller> {
        
        @Override
        public String getDisplayName() {
            return Messages.SharedDirectoryInstaller_DescriptorImpl_displayName();
        }

        @Restricted(NoExternalUse.class)
        @RestrictedSince("0.4")
        public FormValidation doCheckToolHome(@QueryParameter String value) {
            if (value.length() > 0) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(Messages.SharedDirectoryInstaller_no_toolHome());
            }
        }
    }
}
