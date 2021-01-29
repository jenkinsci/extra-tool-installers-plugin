package io.jenkins.plugins.extratoolinstallers.installers;

import java.io.IOException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolInstallerDescriptor;
import hudson.util.FormValidation;

/**
 * A {@link ToolInstaller} that locates an existing tool on the agent, or fails.
 */
public class FindOnPathInstaller extends ToolInstaller {
    @CheckForNull
    private String executableName;

    @CheckForNull
    private String relativePath;

    @DataBoundConstructor
    public FindOnPathInstaller(String label) {
        super(label);
    }

    /**
     * Name of the executable we are to locate.
     * 
     * @return Name, or null if none has been set.
     */
    @CheckForNull
    public String getExecutableName() {
        return Util.fixEmpty(executableName);
    }

    /**
     * Sets {@link #getExecutableName()}.
     * 
     * @param url New value.
     */
    @DataBoundSetter
    public void setExecutableName(@Nullable String executable) {
        this.executableName = Util.fixEmpty(executable);
    }

    /**
     * Directory of the tool's "home", relative to wherever we found the command on
     * the path.
     * 
     * @return The relative path, or null if none has been set (which means ".").
     */
    @CheckForNull
    public String getRelativePath() {
        return Util.fixEmpty(relativePath);
    }

    /**
     * Sets {@link #getRelativePath()}.
     * 
     * @param subdir New value.
     */
    @DataBoundSetter
    public void setRelativePath(@Nullable String relativePath) {
        this.relativePath = Util.fixEmpty(relativePath);
    }

    @Override
    public FilePath performInstallation(@Nonnull ToolInstallation tool, @Nonnull Node node,
            @CheckForNull TaskListener log) throws IOException, InterruptedException {
        final String exeName = getExecutableName();
        if (exeName == null) {
            throw new IllegalArgumentException(Messages.FindOnPathInstaller_executableNameIsEmpty());
        }
        final FilePath executablePath = findExecutableOnNodeOrThrow(exeName, node, log);
        final FilePath parent = executablePath.getParent();
        if (parent == null) {
            // This shouldn't happen, hence not localized.
            throw new IllegalStateException(
                    "Executable (" + exeName + ") found at '" + executablePath + "' has no parent folder");
        }
        final String relPathOrNull = getRelativePath();
        if (relPathOrNull == null || relPathOrNull.equals(".")) {
            return parent;
        } else {
            return parent.child(relPathOrNull);
        }
    }

    @Nonnull
    private FilePath findExecutableOnNodeOrThrow(@Nonnull final String exeName, @Nonnull Node node,
            @CheckForNull final TaskListener logOrNull) throws IOException, InterruptedException {
        final FilePath rootPath = node.getRootPath();
        if (rootPath == null) {
            throw new IllegalStateException(Messages.FindOnPathInstaller_agentIsOffline());
        }
        final FindOnPathCallable nodeOperation = mkCallable(exeName, logOrNull);
        final String absolutePathToExecutable = rootPath.act(nodeOperation);
        final FilePath executablePath = node.createPath(absolutePathToExecutable);
        if (executablePath == null) {
            throw new IllegalStateException(Messages.FindOnPathInstaller_agentIsOffline());
        }
        return executablePath;
    }

    // package access for test purposes only
    @Restricted(NoExternalUse.class)
    @Nonnull
    FindOnPathCallable mkCallable(@Nonnull final String exeName, @CheckForNull final TaskListener logOrNull) {
        return new FindOnPathCallable(exeName, logOrNull);
    }

    @Extension
    @Symbol("findonpath")
    public static class DescriptorImpl extends ToolInstallerDescriptor<FindOnPathInstaller> {
        public String getDisplayName() {
            return Messages.FindOnPathInstaller_DescriptorImpl_displayName();
        }

        public FormValidation doCheckExecutableName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }
    }
}
