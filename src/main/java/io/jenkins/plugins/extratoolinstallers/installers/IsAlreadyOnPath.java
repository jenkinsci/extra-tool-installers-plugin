package io.jenkins.plugins.extratoolinstallers.installers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.google.common.base.Joiner;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
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
public class IsAlreadyOnPath extends ToolInstaller {
    @CheckForNull
    private String executableName;

    @CheckForNull
    private String relativePath;

    @CheckForNull
    private String[] versionCmd;

    @CheckForNull
    private Pattern versionPattern;

    /** Only used if we've been given a pattern that can't be compiled */
    @CheckForNull
    private String versionPatternString;

    @CheckForNull
    private String versionMin;

    @CheckForNull
    private String versionMax;

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
     * @param relativePath New value.
     */
    @DataBoundSetter
    public void setRelativePath(@Nullable String relativePath) {
        this.relativePath = Util.fixEmpty(relativePath);
    }

    /**
     * Command we run in order to test what version we've got.
     * 
     * @return null if not set, else a list where the first element is the command
     *         and the remaining elements are arguments for the command.
     */
    @CheckForNull
    public String[] getVersionCmd() {
        return fixEmpty(versionCmd);
    }

    /**
     * See {@link #getVersionCmd()}.
     * 
     * @return {@link #getVersionCmd()} as a multi-line string.
     */
    @Nonnull
    public String getVersionCmdString() {
        final String[] v = getVersionCmd();
        if (v == null) {
            return "";
        }
        return Joiner.on('\n').join(v);
    }

    /**
     * Sets {@link #getVersionCmd()}.
     * 
     * @param versionCmd New value.
     */
    public void setVersionCmd(String[] versionCmd) {
        if (versionCmd != null) {
            this.versionCmd = Arrays.copyOf(versionCmd, versionCmd.length);
        } else {
            this.versionCmd = null;
        }
    }

    /**
     * See {@link #setVersionCmd(String[])}.
     * 
     * @param versionCmdString New value as multi-line string.
     */
    @DataBoundSetter
    public void setVersionCmdString(String versionCmdString) {
        setVersionCmd(Util.fixNull(versionCmdString).split("\n"));
    }

    private static String[] fixEmpty(String[] l) {
        if (l == null || l.length==0) {
            return null;
        }
        return l;
    }

    /**
     * The regular expression used to parse the output from running
     * {@link #getVersionCmd()}.
     * 
     * @return The regex that was set, or null if not set to a valid value.
     */
    public Pattern getVersionPattern() {
        return versionPattern;
    }

    /**
     * Sets {@link #getVersionPatternString()} and {@link #getVersionPattern()}.
     * 
     * @param versionPattern New value.
     */
    public void setVersionPattern(Pattern versionPattern) {
        this.versionPattern = versionPattern;
        this.versionPatternString = null;
    }

    /**
     * The regular expression used to parse the output from running
     * {@link #getVersionCmd()}.
     * 
     * @return The regex that was set, or null if not set.
     */
    @CheckForNull
    public String getVersionPatternString() {
        if( versionPattern!=null ) {
            return Util.fixEmpty(versionPattern.pattern());
        }
        return Util.fixEmpty(versionPatternString);
    }

    /**
     * Sets {@link #getVersionPatternString()} and {@link #getVersionPattern()}.
     * 
     * @param versionPatternString New value.
     */
    @DataBoundSetter
    public void setVersionPatternString(String versionPatternString) {
        if (Util.fixEmpty(versionPatternString) != null) {
            try {
                this.versionPattern = Pattern.compile(versionPatternString);
                this.versionPatternString = null;
            } catch (PatternSyntaxException ex) {
                this.versionPattern = null;
                this.versionPatternString = versionPatternString;
            }
        } else {
            this.versionPattern = null;
            this.versionPatternString = null;
        }
    }

    /**
     * The minimum version acceptable.
     * 
     * @return The version that was set, or null if not set.
     */
    @CheckForNull
    public String getVersionMin() {
        return versionMin;
    }

    /**
     * Sets {@link #getVersionMin()}.
     * 
     * @param versionMin New value.
     */
    @DataBoundSetter
    public void setVersionMin(String versionMin) {
        this.versionMin = versionMin;
    }


    /**
     * The maximum version acceptable.
     * 
     * @return The version that was set, or null if not set.
     */
    @CheckForNull
    public String getVersionMax() {
        return versionMax;
    }

    /**
     * Sets {@link #getVersionMax()}.
     * 
     * @param versionMax New value.
     */
    @DataBoundSetter
    public void setVersionMax(String versionMax) {
        this.versionMax = versionMax;
    }

    @Override
    public FilePath performInstallation(@Nonnull ToolInstallation tool, @Nonnull Node node,
            @CheckForNull TaskListener log) throws IOException, InterruptedException {
        final String exeName = getExecutableName();
        if (exeName == null) {
            throw new IllegalArgumentException(Messages.IsAlreadyOnPath_executableNameIsEmpty());
        }
        final FilePath executablePath = findExecutableOnNodeOrThrow(exeName, node, log);
        final FilePath parent = executablePath.getParent();
        if (parent == null) {
            // This shouldn't happen, hence not localized.
            throw new IllegalStateException(
                    "Executable (" + exeName + ") found at '" + executablePath + "' has no parent folder");
        }
        final String relPathOrNull = getRelativePath();
        final FilePath resultToReturn;
        if (relPathOrNull == null || relPathOrNull.equals(".")) {
            resultToReturn = parent;
        } else {
            resultToReturn = parent.child(relPathOrNull);
        }
        final String[] vCmd = getVersionCmd();
        final Pattern vPattern = getVersionPattern();
        final String vMax = getVersionMax();
        final String vMin = getVersionMin();
        if (vCmd != null && vPattern != null && (vMin != null || vMax != null)) {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            final Launcher launcher = node.createLauncher(log);
            runCommandOnNode(launcher, resultToReturn, vCmd, output);
            final String cmdOutput = output.toString(StandardCharsets.UTF_8.name());
            final String parsedVersion = parseVersionCmdOutputForVersion(vPattern, cmdOutput);
            final int versionComparisonResult = checkVersionIsInRange(vMin, vMax, parsedVersion);
            if (versionComparisonResult != 0) {
                throw new WrongVersionException(exeName, resultToReturn.getRemote(), parsedVersion, vMin,
                        vMax);
            }
        }
        return resultToReturn;
    }

    @Nonnull
    private FilePath findExecutableOnNodeOrThrow(@Nonnull final String exeName, @Nonnull Node node,
            @CheckForNull final TaskListener logOrNull) throws IOException, InterruptedException {
        final FilePath rootPath = node.getRootPath();
        if (rootPath == null) {
            throw new IllegalStateException(Messages.IsAlreadyOnPath_agentIsOffline());
        }
        final FindOnPathCallable nodeOperation = mkCallable(exeName, logOrNull);
        final String absolutePathToExecutable = rootPath.act(nodeOperation);
        final FilePath executablePath = node.createPath(absolutePathToExecutable);
        if (executablePath == null) {
            throw new IllegalStateException(Messages.IsAlreadyOnPath_agentIsOffline());
        }
        return executablePath;
    }

    // package access for test purposes only
    @Restricted(NoExternalUse.class)
    void runCommandOnNode(final Launcher launcher, final FilePath pwd, final String[] cmd,
            final OutputStream output) throws IOException, InterruptedException {
        launcher.launch().cmds(cmd).stdout(output).pwd(pwd).join();
    }

    // package access for test purposes only
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
                final int cmp = compareVersions(versionMin, versionMax);
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
            if (Util.fixEmpty(versionMin) == null ) {
                if (Util.fixEmpty(versionMax) == null ) {
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
            final String parsedVersion = parseVersionCmdOutputForVersion(versionPattern, versionTestString);
            if (Util.fixEmpty(parsedVersion) == null) {
                return FormValidation.warning(Messages.IsAlreadyOnPath_versionPatternDidNotMatch());
            }
            final int versionComparisonResult = checkVersionIsInRange(versionMin, versionMax, parsedVersion);
            if (versionComparisonResult < 0) {
                return FormValidation.warning(Messages.IsAlreadyOnPath_versionIsTooLow(parsedVersion, versionMin));
            }
            if (versionComparisonResult > 0) {
                return FormValidation.warning(Messages.IsAlreadyOnPath_versionIsTooHigh(parsedVersion, versionMax));
            }
            return FormValidation.ok(Messages.IsAlreadyOnPath_versionIsOk(parsedVersion));
        }
    }

    /**
     * Extracts a version string from a command's output.
     * 
     * @param versionPattern The pattern we're using to find the version.
     * @param cmdOutput      The command output we are parsing.
     * @return null if no match, else the concatenation of all capturing groups
     *         within the pattern.
     */
    // default access for unit-test purposes
    @Restricted(NoExternalUse.class)
    static String parseVersionCmdOutputForVersion(Pattern versionPattern, String cmdOutput) {
        for (final String cmdLine : cmdOutput.split("\\R")) {
            final Matcher matcher = versionPattern.matcher(cmdLine);
            if (matcher.matches()) {
                final int gc = matcher.groupCount();
                final StringBuilder result = new StringBuilder();
                for (int g = 1; g <= gc; g++) {
                    final String group = matcher.group(g);
                    if (group != null) {
                        result.append(group);
                    }
                }
                return result.toString();
            }
        }
        return null;
    }

    /**
     * Compares a version string to the specified min & max.
     * 
     * @param versionMin    The minimum acceptable version (inclusive, i.e. this
     *                      version is acceptable). Can be null if there is no
     *                      minimum.
     * @param versionMax    The maximum acceptable version (inclusive, i.e. this
     *                      version is acceptable). Can be null if there is no
     *                      maximum.
     * @param actualVersion The version to be checked.
     * @return 0 if the version is acceptable, -ve if the version is below the
     *         minimum, +ve is the version is above the maximum.
     */
    // default access for unit-test purposes
    @Restricted(NoExternalUse.class)
    static int checkVersionIsInRange(@Nullable String versionMin, @Nullable String versionMax, @Nullable String actualVersion) {
        if (Util.fixEmpty(versionMin) != null) {
            final int cmpParsedToMin = compareVersions(actualVersion, versionMin);
            if (cmpParsedToMin < 0) {
                return -1; // parsedVersion is lower than versionMin
            }
        }
        if (Util.fixEmpty(versionMax) != null) {
            final int cmpParsedToMax = compareVersions(actualVersion, versionMax);
            if (cmpParsedToMax > 0) {
                return 1; // parsedVersion is higher than versionMax
            }
        }
        return 0;
    }

    /**
     * @return 0 if versions are equivalent, +ve if compoundVersionA is higher than
     *         compoundVersionB, -ve if compoundVersionA is lower.
     */
    // default access for descriptor
    @Restricted(NoExternalUse.class)
    static int compareVersions(@Nullable String compoundVersionA, @Nullable String compoundVersionB) {
        final String[] splitA = compoundVersionA==null ? new String[0] : compoundVersionA.split("\\.", -1);
        final String[] splitB = compoundVersionB==null ? new String[0] : compoundVersionB.split("\\.", -1);
        final int lengthA = splitA.length;
        final int lengthB = splitB.length;
        final int highestLength = Math.max(lengthA, lengthB);
        for (int i = 0; i < highestLength; i++) {
            final String partA = lengthA > i ? splitA[i] : null;
            final String partB = lengthB > i ? splitB[i] : null;
            final int result = compareNullableVersionParts(partA, partB);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    /**
     * @return 0 if versions are equivalent, +ve if partA is higher than partB, -ve
     *         if partA is lower.
     */
    private static int compareNullableVersionParts(@Nullable String partA, @Nullable String partB) {
        if (partA == null) {
            if (partB == null) {
                return 0;
            } else {
                return -1;
            }
        } else {
            if (partB == null) {
                return 1;
            } else {
                return compareVersionParts(partA, partB);
            }
        }
    }

    /**
     * Compares two strings on the assumption that they have a numerical start
     * followed by lexicographical part.
     * 
     * @return 0 if versions are equivalent, +ve if a is higher than b, -ve if a is
     *         lower.
     */
    private static int compareVersionParts(@Nonnull String a, @Nonnull String b) {
        final int ai = findIndexOfFirstNonnumericalCharacter(a);
        final int bi = findIndexOfFirstNonnumericalCharacter(b);
        final String aNumberString;
        final String bNumberString;
        final String aRemainder;
        final String bRemainder;
        if (ai >= 0) {
            aNumberString = a.substring(0, ai);
            aRemainder = a.substring(ai);
        } else {
            aNumberString = a;
            aRemainder = "";
        }
        if (bi >= 0) {
            bNumberString = b.substring(0, bi);
            bRemainder = b.substring(bi);
        } else {
            bNumberString = b;
            bRemainder = "";
        }
        final long aNumber = aNumberString.isEmpty() ? -1L : Long.parseLong(aNumberString);
        final long bNumber = bNumberString.isEmpty() ? -1L : Long.parseLong(bNumberString);
        if (aNumber > bNumber) {
            return 1;
        }
        if (aNumber < bNumber) {
            return -1;
        }
        return aRemainder.compareTo(bRemainder);
    }

    private static int findIndexOfFirstNonnumericalCharacter(@Nonnull String s) {
        final int l = s.length();
        for (int i = 0; i < l; i++) {
            final char c = s.charAt(i);
            if (!Character.isDigit(c)) {
                return i;
            }
        }
        return -l;
    }
}
