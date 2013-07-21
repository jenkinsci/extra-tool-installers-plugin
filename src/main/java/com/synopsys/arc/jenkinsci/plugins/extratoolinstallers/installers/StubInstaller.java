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
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.CommandInstaller;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolInstallerDescriptor;
import hudson.util.FormValidation;
import java.io.IOException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Stub installer, which doesn't perform installation.
 * Can be used in order to notify users about unsupported platform (and optionally fail the build)
 * @author Oleg Nenashev <nenashev@synopsys.com>, Synopsys Inc.
 * @since 0.2
 */
public class StubInstaller extends ToolInstaller {
    private final String message;
    private final boolean failTheBuild;
    
    @DataBoundConstructor
    public StubInstaller(String label, String message, boolean failTheBuild) {
        super(label);
        this.message = hudson.Util.fixEmptyAndTrim(message);
        this.failTheBuild = failTheBuild;
    }
    
    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log) 
            throws IOException, InterruptedException 
    {
        FilePath dir = preferredLocation(tool, node);       
        String messagePrefix = "["+tool.getName()+"] - ";
        String outMessage = messagePrefix + (message != null ? message : Messages.StubInstaller_defaultMessage());
        log.getLogger().println(outMessage);
        
        if (failTheBuild) {
            throw new IOException(messagePrefix+"Installation has been interrupted");
        }    
        return dir;
    }
    
    @Extension
    public static class DescriptorImpl extends ToolInstallerDescriptor<CommandInstaller> {
        
        @Override
        public String getDisplayName() {
            return Messages.StubInstaller_displayName();
        }

        public FormValidation doCheckMessage(@QueryParameter String value) {
            if (value.length() > 0) {
                return FormValidation.ok();
            } else {
                return FormValidation.warning(Messages.StubInstaller_noMessage());
            }
        }
    }
}
