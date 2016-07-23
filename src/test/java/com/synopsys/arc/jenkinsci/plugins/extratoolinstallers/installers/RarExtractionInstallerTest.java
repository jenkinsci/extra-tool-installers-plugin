/*
 * The MIT License
 *
 * Copyright 2016 Martin Hjelmqvist.
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

import com.cloudbees.jenkins.plugins.customtools.CustomTool;
import com.synopsys.arc.jenkinsci.plugins.customtools.versions.ToolVersionConfig;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.Node;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import com.cloudbees.jenkins.plugins.customtools.CustomTool.DescriptorImpl;
import com.cloudbees.jenkins.plugins.customtools.CustomToolInstallWrapper;
import com.synopsys.arc.jenkinsci.plugins.customtools.multiconfig.MulticonfigWrapperOptions;

/**
 * Simulating a Jenkins instance and automatically tests the RarExtractionInstaller.
 * Also performs a configuration round-trip.
 *
 * @author Martin Hjelmqvist <martin@hjelmqvist.eu>.
 */
public class RarExtractionInstallerTest {

    private static Logger log = Logger.getLogger(RarExtractionInstallerTest.class.getName());

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    
    /**
     * Supposed to automatically test the RarExtractionInstaller by setting up a Jenkins instance, 
     * configuring a custom tool and a freestyle project. Builds on a master, then on a slave node.
     * Currently, it builds all the way, but the RarExtractionInstaller is never executed.
     */
    @Test
    public void testPerformInstallation() throws Exception {
        DescriptorImpl tools = r.jenkins.getDescriptorByType(CustomTool.DescriptorImpl.class);

        List<ToolInstaller> installers = new ArrayList<ToolInstaller>();
        installers.add(new RarExtractionInstaller("MyRarInstaller", getClass().getResource("testArchive.rar").toString(), false));
        List<ToolProperty<ToolInstallation>> properties = new ArrayList<ToolProperty<ToolInstallation>>();
        properties.add(new InstallSourceProperty(installers));

        CustomTool tool = new CustomTool("MyCustomTool", "./", properties, "./", null, ToolVersionConfig.DEFAULT, null);
        tools.setInstallations(tool);

        FreeStyleProject p = r.createFreeStyleProject();

        CustomToolInstallWrapper.SelectedTool selectedTool = new CustomToolInstallWrapper.SelectedTool("MyCustomTool");
        CustomToolInstallWrapper wrapper = new CustomToolInstallWrapper(
                new CustomToolInstallWrapper.SelectedTool[]{selectedTool}, MulticonfigWrapperOptions.DEFAULT, false);

        p.getBuildWrappersList().add(wrapper);

        // Build only on master.
        r.buildAndAssertSuccess(p);

        // Build only on slave.
        Node slave = r.createSlave(Label.get("MySlave"));
        r.jenkins.setNumExecutors(0);
        p.setAssignedNode(slave);
        r.buildAndAssertSuccess(p);
    }
}
