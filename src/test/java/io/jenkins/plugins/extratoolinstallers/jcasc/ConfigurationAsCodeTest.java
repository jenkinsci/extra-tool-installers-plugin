package io.jenkins.plugins.extratoolinstallers.jcasc;

import com.synopsys.arc.jenkinsci.plugins.extratoolinstallers.installers.SharedDirectoryInstaller;
import com.synopsys.arc.jenkinsci.plugins.extratoolinstallers.installers.StubInstaller;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolProperty;
import hudson.tools.ToolPropertyDescriptor;
import hudson.util.DescribableList;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import io.jenkins.plugins.extratoolinstallers.installers.AnyOfInstaller;
import io.jenkins.plugins.extratoolinstallers.installers.AuthenticatedZipExtractionInstaller;
import io.jenkins.plugins.extratoolinstallers.installers.IsAlreadyOnPath;
import io.jenkins.plugins.generic_tool.GenericToolInstallation;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.ansible.AnsibleInstallation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkinsConfiguredWithCode
class ConfigurationAsCodeTest {

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    void should_support_configuration_as_code(JenkinsConfiguredWithCodeRule r) {

        // Just ensure that the structure is correct for maven tool
        MavenInstallation mavenInstallation = Jenkins.get().getDescriptorByType(MavenInstallation.DescriptorImpl.class).getInstallations()[0];
        assertEquals("maven-3.9.4", mavenInstallation.getName());
        DescribableList<ToolProperty<?>, ToolPropertyDescriptor> mavenToolInstallers = mavenInstallation.getProperties();
        assertEquals(1, mavenToolInstallers.size());
        InstallSourceProperty mavenInstallSourceProperty = (InstallSourceProperty)mavenToolInstallers.get(0);
        assertEquals(1, mavenInstallSourceProperty.installers.size());

        // AnyOfInstaller is set with 3 installers
        AnyOfInstaller anyOfInstaller = (AnyOfInstaller)mavenInstallSourceProperty.installers.get(0);
        assertEquals(3, anyOfInstaller.getInstallers().installers.size());

        AuthenticatedZipExtractionInstaller installer1 = (AuthenticatedZipExtractionInstaller)anyOfInstaller.getInstallers().installers.get(0);
        AuthenticatedZipExtractionInstaller installer2 = (AuthenticatedZipExtractionInstaller)anyOfInstaller.getInstallers().installers.get(1);
        StubInstaller installer3 = (StubInstaller)anyOfInstaller.getInstallers().installers.get(2);

        // Installer 1
        assertEquals("linux", installer1.getLabel());
        assertEquals("private-registry-credentials-id", installer1.getCredentialsId());
        assertEquals("https://private.registry.company.org/maven/3.9.5/binaries/apache-maven-3.9.5-bin.tar.gz", installer1.getUrl());

        // Installer 2
        assertEquals("linux", installer2.getLabel());
        assertNull(installer2.getCredentialsId());
        assertEquals("https://archive.apache.org/dist/maven/maven-3/3.9.5/binaries/apache-maven-3.9.5-bin.tar.gz", installer2.getUrl());

        // Installer 3
        assertEquals("!linux", installer3.getLabel());
        assertFalse(installer3.isFailOnSubstitution());
        assertTrue(installer3.isFailTheBuild());
        assertEquals("Unable to install on this node", installer3.getMessage());

        // Just ensure that the structure is correct for generic tool
        GenericToolInstallation genericInstallation = Jenkins.get().getDescriptorByType(GenericToolInstallation.DescriptorImpl.class).getInstallations()[0];
        assertEquals("python3", genericInstallation.getName());
        DescribableList<ToolProperty<?>, ToolPropertyDescriptor> genericToolInstallers = genericInstallation.getProperties();
        assertEquals(1, genericToolInstallers.size());
        InstallSourceProperty genericInstallSourceProperty = (InstallSourceProperty)genericToolInstallers.get(0);
        assertEquals(1, genericInstallSourceProperty.installers.size());

        IsAlreadyOnPath isAlreadyOnPathProperty = (IsAlreadyOnPath)genericInstallSourceProperty.installers.get(0);

        // Installer
        assertEquals("linux", isAlreadyOnPathProperty.getLabel());
        assertEquals("3.10.0", isAlreadyOnPathProperty.getVersionMin());
        assertEquals("3.12.0", isAlreadyOnPathProperty.getVersionMax());
        assertEquals("Python (.*)", isAlreadyOnPathProperty.getVersionPatternString());

        // Just ensure that the structure is correct for generic tool
        AnsibleInstallation ansibleInstallation = Jenkins.get().getDescriptorByType(AnsibleInstallation.DescriptorImpl.class).getInstallations()[0];
        assertEquals("ansible", ansibleInstallation.getName());
        DescribableList<ToolProperty<?>, ToolPropertyDescriptor> ansibleToolInstaller = ansibleInstallation.getProperties();
        assertEquals(1, ansibleToolInstaller.size());
        InstallSourceProperty ansibleInstallSourceProperty = (InstallSourceProperty)ansibleInstallation.getProperties().get(0);
        assertEquals(1, ansibleInstallSourceProperty.installers.size());

        SharedDirectoryInstaller sharedDirectoryInstaller = (SharedDirectoryInstaller)ansibleInstallSourceProperty.installers.get(0);
        assertEquals("linux", sharedDirectoryInstaller.getLabel());
        assertTrue(sharedDirectoryInstaller.isFailOnSubstitution());
        assertEquals("${HOME}/.local/bin/ansible", sharedDirectoryInstaller.getToolHome());

    }
}
