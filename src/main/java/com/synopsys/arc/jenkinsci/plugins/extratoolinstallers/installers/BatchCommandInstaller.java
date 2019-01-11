/*
 * The MIT License
 *
 * Copyright (c) 2009, Sun Microsystems, Inc.
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
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tasks.CommandInterpreter;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstallerDescriptor;
import hudson.util.FormValidation;
import java.io.IOException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Installs tool via script execution of Batch script.
 * Inspired by the {@link hudson.tools.CommandInstaller} from the Jenkins core.
 * @since 0.1
 * @deprecated {@link hudson.tools.BatchCommandInstaller} is now available in the core.
 */
@Deprecated
public class BatchCommandInstaller extends AbstractExtraToolInstaller {

    /**
     * Command to execute, similar to {@link CommandInterpreter#command}.
     */
    private final String command;

    @DataBoundConstructor
    public BatchCommandInstaller(String label, String command, String toolHome, boolean failOnSubstitution) {
        super(label, toolHome, failOnSubstitution);
        this.command = fixCrLf(command);
    }

    /**
     * Fix CR/LF and always make it Unix style.
     */
    //TODO: replace by windows style
    private static String fixCrLf(String s) {
        // eliminate CR
        int idx;
        while((idx=s.indexOf("\r\n"))!=-1)
            s = s.substring(0,idx)+s.substring(idx+1);
        return s;
    }
    
    public String getCommand() {
        return command;
    }

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log) throws IOException, InterruptedException {
        String substitutedHome = substituteNodeVariablesValidated("Tool Home", getToolHome(), node);   
        
        FilePath dir = preferredLocation(tool, node);
        // XXX support Windows batch scripts, Unix scripts with interpreter line, etc. (see CommandInterpreter subclasses)
        FilePath script = dir.createTextTempFile("hudson", ".bat", command);
        try {
            String[] cmd = {"cmd", "/c", "call", script.getRemote()};
            int r = node.createLauncher(log).launch().cmds(cmd).stdout(log).pwd(dir).join();
            if (r != 0) {
                throw new IOException("Command returned status " + r);
            }
        } finally {
            script.delete();
        }
        return dir.child(substitutedHome);
    }
    
    @Extension
    public static class DescriptorImpl extends ToolInstallerDescriptor<BatchCommandInstaller> {

        @Override
        public String getDisplayName() {
            return Messages.BatchCommandInstaller_DescriptorImpl_displayName();
        }

        public FormValidation doCheckCommand(@QueryParameter String value) {
            if (value.length() > 0) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(Messages.BatchCommandInstaller_no_command());
            }
        }

        public FormValidation doCheckToolHome(@QueryParameter String value) {
            if (value.length() > 0) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(Messages.BatchCommandInstaller_no_toolHome());
            }
        }
    }

    /**
     * Prevents the {@link BatchCommandInstaller} from being selectable for new
     * installers.
     */
    @Extension
    public static class BatchCommandInstallerDescriptorVisibilityFilter extends DescriptorVisibilityFilter {
        @SuppressWarnings("rawtypes")
        @Override
        public boolean filter(Object context, Descriptor descriptor) {
            return !(descriptor instanceof DescriptorImpl);
        }
    }
}
