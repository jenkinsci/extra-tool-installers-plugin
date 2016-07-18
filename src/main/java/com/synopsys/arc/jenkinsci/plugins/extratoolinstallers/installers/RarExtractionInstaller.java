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
import jenkins.MasterToSlaveFileCallable;
import hudson.ProxyConfiguration;
import hudson.Functions;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstallerDescriptor;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Installs a tool by downloading and unpacking a RAR file.
 * Installer is inspired by "ZipExtractionInstaller" from the Custom Tools plugin.
 * @author Martin Hjelmqvist <martin@hjelmqvist.eu>.
 */
public class RarExtractionInstaller extends AbstractExtraToolInstaller {

    /**
     * URL of a RAR file which should be downloaded in case the tool is missing.
     */
    private final String toolHome;

    /**
     * Messages to be put in Messages.java later.
     */
    private static final String RAR_EXTRACTION_INSTALLER_DISPLAY_NAME = "Extract *.rar";
    private static final String RAR_EXTRACTION_INSTALLER_BAD_CONNECTION = "Server rejected connection.";
    private static final String RAR_EXTRACTION_INSTALLER_MALFORMED_URL = "Malformed URL.";
    private static final String RAR_EXTRACTION_INSTALLER_COULD_NOT_CONNECT = "Could not connect to URL.";

    @DataBoundConstructor
    public RarExtractionInstaller(String label, String toolHome,
            boolean failOnSubstitution) {
        super(label, toolHome, failOnSubstitution);
        this.toolHome = toolHome;
    }

    public String getUrl() {
        return toolHome;
    }

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log) throws IOException, InterruptedException {
        FilePath dir = preferredLocation(tool, node);
        RarFilePath rarDir = new RarFilePath(dir);

        if (rarDir.installIfNecessaryFrom(new URL(toolHome), log, "Unpacking " + toolHome + " to " + dir + " on " + node.getDisplayName())) {
            rarDir.act(new ChmodRecAPlusX());
        }
        return dir;

    }

    @Extension
    public static class DescriptorImpl extends ToolInstallerDescriptor<RarExtractionInstaller> {

        @Override
        public String getDisplayName() {
            return RAR_EXTRACTION_INSTALLER_DISPLAY_NAME;
        }

        public FormValidation doCheckUrl(@QueryParameter String value) {
            try {
                URLConnection conn = ProxyConfiguration.open(new URL(value));
                conn.connect();
                if (conn instanceof HttpURLConnection) {
                    if (((HttpURLConnection) conn).getResponseCode() != HttpURLConnection.HTTP_OK) {
                        return FormValidation.error(RAR_EXTRACTION_INSTALLER_BAD_CONNECTION);
                    }
                }
                return FormValidation.ok();
            } catch (MalformedURLException x) {
                return FormValidation.error(RAR_EXTRACTION_INSTALLER_MALFORMED_URL);
            } catch (IOException x) {
                return FormValidation.error(x, RAR_EXTRACTION_INSTALLER_COULD_NOT_CONNECT);
            }
        }

    }

    /**
     * Inspired by "ZipExtractionInstaller" from the Custom Tools plugin.
     */
    static class ChmodRecAPlusX extends MasterToSlaveFileCallable<Void> {

        private static final long serialVersionUID = 1L;

        public Void invoke(File d, VirtualChannel channel) throws IOException {
            if (!Functions.isWindows()) {
                process(d);
            }
            return null;
        }

        private void process(File f) {
            if (f.isFile()) {
                f.setExecutable(true, false);
            } else {
                File[] kids = f.listFiles();
                if (kids != null) {
                    for (File kid : kids) {
                        process(kid);
                    }
                }
            }
        }
    }
}
