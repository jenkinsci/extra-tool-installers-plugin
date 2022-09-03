package io.jenkins.plugins.extratoolinstallers.installers;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolInstallerDescriptor;
import hudson.util.FormValidation;
import io.jenkins.plugins.extratoolinstallers.installers.utils.VersionChecker;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A {@link ToolInstaller} that locates an existing tool on the agent, or fails.
 */
public class IsAlreadyOnPath extends VersionCheckingToolInstaller {
    @CheckForNull
    private String executableName;

    /**
     * Constructor that sets mandatory fields.
     *
     * @param label The {@link ToolInstaller#getLabel()}.
     */
    @DataBoundConstructor
    public IsAlreadyOnPath(String label) {
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
     * @param executable New value.
     */
    @DataBoundSetter
    public void setExecutableName(@Nullable String executable) {
        this.executableName = Util.fixEmpty(executable);
    }

    @Nonnull
    @Override
    FilePath findExecutableOnNodeOrThrow(@Nonnull Node node,
                                         @CheckForNull final TaskListener logOrNull) throws IOException, InterruptedException {
        final FindOnPathCallable nodeOperation = mkCallable(logOrNull);
        final FilePath rootPath = node.getRootPath();
        if (rootPath == null) {
            throw new IllegalStateException(Messages.IsAlreadyOnPath_agentIsOffline());
        }
        final String absolutePathToExecutable = rootPath.act(nodeOperation);
        final FilePath executablePath = node.createPath(absolutePathToExecutable);
        if (executablePath == null) {
            throw new IllegalStateException(Messages.IsAlreadyOnPath_agentIsOffline());
        }
        return executablePath;
    }

    @Nonnull
    @Override
    FindOnPathCallable mkCallable(@CheckForNull final TaskListener logOrNull) {
        final String exeName = getExecutableName();
        if (exeName == null) {
            throw new IllegalArgumentException(Messages.IsAlreadyOnPath_executableNameIsEmpty());
        }
        return mkCallable(exeName, logOrNull);
    }

    @Restricted(NoExternalUse.class)
    @Nonnull
    FindOnPathCallable mkCallable(@Nonnull final String exeName, @CheckForNull final TaskListener logOrNull) {
        return new FindOnPathCallable(exeName, logOrNull);
    }

    /**
     * Descriptor for {@link IsAlreadyOnPath}.
     */
    @Extension
    @Symbol("findonpath")
    public static class DescriptorImpl extends ToolInstallerDescriptor<IsAlreadyOnPath> {
        public String getDisplayName() {
            return Messages.IsAlreadyOnPath_DescriptorImpl_displayName();
        }

        public FormValidation doCheckExecutableName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckVersionCmdString(@QueryParameter String value) {
            if (Util.fixEmpty(value) == null) {
                return FormValidation.ok(Messages.IsAlreadyOnPath_noVersionValidation());
            }
            if (value.contains(" ") && !value.contains("\n")) {
                return FormValidation.warning(Messages.IsAlreadyOnPath_versionCmdContainsSpaceButHasNoArguments());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckVersionPatternString(@QueryParameter String versionCmdString,
                                                          @QueryParameter String versionPatternString) {
            if (Util.fixEmpty(versionCmdString) == null) {
                return FormValidation.ok();
            }
            if (Util.fixEmpty(versionPatternString) == null) {
                return FormValidation.error(Messages.IsAlreadyOnPath_versionPatternIsEmpty());
            }
            try {
                Pattern.compile(versionPatternString);
            } catch (PatternSyntaxException ex) {
                return FormValidation.error(ex,
                        Messages.IsAlreadyOnPath_versionPatternIsInvalid(versionPatternString));
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckVersionMin(@QueryParameter String versionCmdString,
                                                @QueryParameter String versionPatternString, @QueryParameter String versionMin,
                                                @QueryParameter String versionMax) {
            if (Util.fixEmpty(versionCmdString) != null && Util.fixEmpty(versionPatternString) != null) {
                if (Util.fixEmpty(versionMin) == null && Util.fixEmpty(versionMax) == null) {
                    return FormValidation.error(Messages.IsAlreadyOnPath_versionMinMaxNotSpecified());
                }
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckVersionMax(@QueryParameter String versionMin, @QueryParameter String versionMax) {
            if (Util.fixEmpty(versionMin) != null && Util.fixEmpty(versionMax) != null) {
                final int cmp = VersionChecker.compareVersions(versionMin, versionMax);
                if (cmp > 0) {
                    return FormValidation.error(Messages.IsAlreadyOnPath_versionMaxMustNotBeLessThanMinimum(versionMin));
                }
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckVersionTestString(@QueryParameter String versionTestString,
                                                       @QueryParameter String versionCmdString, @QueryParameter String versionPatternString,
                                                       @QueryParameter String versionMin, @QueryParameter String versionMax) {
            if (Util.fixEmpty(versionTestString) == null) {
                return FormValidation.ok();
            }
            if (Util.fixEmpty(versionCmdString) == null) {
                return FormValidation.warning(Messages.IsAlreadyOnPath_noVersionValidation() + "\n"
                        + Messages.IsAlreadyOnPath_versionCmdIsEmpty());
            }
            if (Util.fixEmpty(versionPatternString) == null) {
                return FormValidation.warning(Messages.IsAlreadyOnPath_noVersionValidation() + "\n"
                        + Messages.IsAlreadyOnPath_versionPatternIsEmpty());
            }
            if (Util.fixEmpty(versionMin) == null) {
                if (Util.fixEmpty(versionMax) == null) {
                    return FormValidation.warning(Messages.IsAlreadyOnPath_noVersionValidation() + "\n"
                            + Messages.IsAlreadyOnPath_versionMinMaxNotSpecified());
                }
            }
            final Pattern versionPattern;
            try {
                versionPattern = Pattern.compile(versionPatternString);
            } catch (PatternSyntaxException ex) {
                return FormValidation.warning(ex, Messages.IsAlreadyOnPath_noVersionValidation() + "\n" +
                        Messages.IsAlreadyOnPath_versionPatternIsInvalid(versionPatternString));
            }
            final String parsedVersion = VersionChecker.parseVersionCmdOutputForVersion(versionPattern, versionTestString);
            if (Util.fixEmpty(parsedVersion) == null) {
                return FormValidation.warning(Messages.IsAlreadyOnPath_versionPatternDidNotMatch());
            }
            final int versionComparisonResult = VersionChecker.checkVersionIsInRange(versionMin, versionMax, parsedVersion);
            if (versionComparisonResult < 0) {
                return FormValidation.warning(Messages.IsAlreadyOnPath_versionIsTooLow(parsedVersion, versionMin));
            }
            if (versionComparisonResult > 0) {
                return FormValidation.warning(Messages.IsAlreadyOnPath_versionIsTooHigh(parsedVersion, versionMax));
            }
            return FormValidation.ok(Messages.IsAlreadyOnPath_versionIsOk(parsedVersion));
        }
    }
}
