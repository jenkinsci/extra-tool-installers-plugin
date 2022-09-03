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
 * A {@link ToolInstaller} that tool is already installed in the specified directory and fails if it is not.
 */
public class IsAlreadyInGivenDirectory extends VersionCheckingToolInstaller {
    @CheckForNull
    private String executablePath;

    /**
     * Constructor that sets mandatory fields.
     *
     * @param label The {@link ToolInstaller#getLabel()}.
     */
    @DataBoundConstructor
    public IsAlreadyInGivenDirectory(String label) {
        super(label);
    }

    /**
     * Path to the executable file.
     *
     * @return Path, or null if none has been set.
     */
    @CheckForNull
    public String getExecutablePath() {
        return Util.fixEmpty(executablePath);
    }

    /**
     * Sets {@link #getExecutablePath()}.
     *
     * @param executablePath new value.
     */
    @DataBoundSetter
    public void setExecutablePath(@Nullable String executablePath) {
        this.executablePath = Util.fixEmpty(executablePath);
    }

    @Nonnull
    @Override
    FilePath findExecutableOnNodeOrThrow(@Nonnull Node node,
                                         @CheckForNull final TaskListener logOrNull) throws IOException, InterruptedException {
        final FindInDirCallable nodeOperation = mkCallable(logOrNull);
        final FilePath rootPath = node.getRootPath();
        if (rootPath == null) {
            throw new IllegalStateException(Messages.IsAlreadyInGivenDirectory_agentIsOffline());
        }
        final String absolutePathToExecutable = rootPath.act(nodeOperation);
        final FilePath executablePath = node.createPath(absolutePathToExecutable);
        if (executablePath == null) {
            throw new IllegalStateException(Messages.IsAlreadyInGivenDirectory_agentIsOffline());
        }
        return executablePath;
    }

    @Nonnull
    @Override
    FindInDirCallable mkCallable(@CheckForNull final TaskListener logOrNull) {
        final String executablePath = getExecutablePath();
        if (executablePath == null) {
            throw new IllegalArgumentException(Messages.IsAlreadyInGivenDirectory_executablePathIsEmpty());
        }
        return mkCallable(executablePath, logOrNull);
    }

    @Nonnull
    FindInDirCallable mkCallable(@Nonnull final String executablePath, @CheckForNull final TaskListener logOrNull) {
        return new FindInDirCallable(executablePath, logOrNull);
    }

    /**
     * Descriptor for {@link IsAlreadyInGivenDirectory}.
     */
    @Extension
    @Symbol("findinspecifieddir")
    public static class DescriptorImpl extends ToolInstallerDescriptor<IsAlreadyInGivenDirectory> {
        public String getDisplayName() {
            return Messages.IsAlreadyInGivenDirectory_DescriptorImpl_displayName();
        }

        public FormValidation doCheckExecutablePath(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckVersionCmdString(@QueryParameter String value) {
            if (Util.fixEmpty(value) == null) {
                return FormValidation.ok(Messages.IsAlreadyInGivenDirectory_noVersionValidation());
            }
            if (value.contains(" ") && !value.contains("\n")) {
                return FormValidation.warning(Messages.IsAlreadyInGivenDirectory_versionCmdContainsSpaceButHasNoArguments());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckVersionPatternString(@QueryParameter String versionCmdString,
                                                          @QueryParameter String versionPatternString) {
            if (Util.fixEmpty(versionCmdString) == null) {
                return FormValidation.ok();
            }
            if (Util.fixEmpty(versionPatternString) == null) {
                return FormValidation.error(Messages.IsAlreadyInGivenDirectory_versionPatternIsEmpty());
            }
            try {
                Pattern.compile(versionPatternString);
            } catch (PatternSyntaxException ex) {
                return FormValidation.error(ex,
                        Messages.IsAlreadyInGivenDirectory_versionPatternIsInvalid(versionPatternString));
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckVersionMin(@QueryParameter String versionCmdString,
                                                @QueryParameter String versionPatternString, @QueryParameter String versionMin,
                                                @QueryParameter String versionMax) {
            if (Util.fixEmpty(versionCmdString) != null && Util.fixEmpty(versionPatternString) != null) {
                if (Util.fixEmpty(versionMin) == null && Util.fixEmpty(versionMax) == null) {
                    return FormValidation.error(Messages.IsAlreadyInGivenDirectory_versionMinMaxNotSpecified());
                }
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckVersionMax(@QueryParameter String versionMin, @QueryParameter String versionMax) {
            if (Util.fixEmpty(versionMin) != null && Util.fixEmpty(versionMax) != null) {
                final int cmp = VersionChecker.compareVersions(versionMin, versionMax);
                if (cmp > 0) {
                    return FormValidation.error(Messages.IsAlreadyInGivenDirectory_versionMaxMustNotBeLessThanMinimum(versionMin));
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
                return FormValidation.warning(Messages.IsAlreadyInGivenDirectory_noVersionValidation() + "\n"
                        + Messages.IsAlreadyInGivenDirectory_versionCmdIsEmpty());
            }
            if (Util.fixEmpty(versionPatternString) == null) {
                return FormValidation.warning(Messages.IsAlreadyInGivenDirectory_noVersionValidation() + "\n"
                        + Messages.IsAlreadyInGivenDirectory_versionPatternIsEmpty());
            }
            if (Util.fixEmpty(versionMin) == null) {
                if (Util.fixEmpty(versionMax) == null) {
                    return FormValidation.warning(Messages.IsAlreadyInGivenDirectory_noVersionValidation() + "\n"
                            + Messages.IsAlreadyInGivenDirectory_versionMinMaxNotSpecified());
                }
            }
            final Pattern versionPattern;
            try {
                versionPattern = Pattern.compile(versionPatternString);
            } catch (PatternSyntaxException ex) {
                return FormValidation.warning(ex, Messages.IsAlreadyInGivenDirectory_noVersionValidation() + "\n" +
                        Messages.IsAlreadyInGivenDirectory_versionPatternIsInvalid(versionPatternString));
            }
            final String parsedVersion = VersionChecker.parseVersionCmdOutputForVersion(versionPattern, versionTestString);
            if (Util.fixEmpty(parsedVersion) == null) {
                return FormValidation.warning(Messages.IsAlreadyInGivenDirectory_versionPatternDidNotMatch());
            }
            final int versionComparisonResult = VersionChecker.checkVersionIsInRange(versionMin, versionMax, parsedVersion);
            if (versionComparisonResult < 0) {
                return FormValidation.warning(Messages.IsAlreadyInGivenDirectory_versionIsTooLow(parsedVersion, versionMin));
            }
            if (versionComparisonResult > 0) {
                return FormValidation.warning(Messages.IsAlreadyInGivenDirectory_versionIsTooHigh(parsedVersion, versionMax));
            }
            return FormValidation.ok(Messages.IsAlreadyInGivenDirectory_versionIsOk(parsedVersion));
        }
    }
}
