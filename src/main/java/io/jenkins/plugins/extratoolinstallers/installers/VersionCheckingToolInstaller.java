package io.jenkins.plugins.extratoolinstallers.installers;

import com.google.common.base.Joiner;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import io.jenkins.plugins.extratoolinstallers.installers.utils.VersionChecker;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A modification of {@link ToolInstaller} that is able to check whether a tool is of specific version.
 */
public abstract class VersionCheckingToolInstaller extends ToolInstaller {
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
    public VersionCheckingToolInstaller(String label) {
        super(label);
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
        final FilePath executablePath = findExecutableOnNodeOrThrow(node, log);
        final FilePath parent = executablePath.getParent();
        if (parent == null) {
            // This shouldn't happen, hence not localized.
            throw new IllegalStateException(
                    "Executable " + executablePath + " has no parent folder");
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
            final String parsedVersion = VersionChecker.parseVersionCmdOutputForVersion(vPattern, cmdOutput);
            final int versionComparisonResult = VersionChecker.checkVersionIsInRange(vMin, vMax, parsedVersion);
            if (versionComparisonResult != 0) {
                throw new WrongVersionException(executablePath.getRemote(), resultToReturn.getRemote(), parsedVersion, vMin,
                        vMax);
            }
        }
        return resultToReturn;
    }

    @Nonnull
    abstract FilePath findExecutableOnNodeOrThrow(@Nonnull Node node, @CheckForNull final TaskListener logOrNull) throws IOException, InterruptedException;

    // package access for test purposes only
    @Restricted(NoExternalUse.class)
    void runCommandOnNode(final Launcher launcher, final FilePath pwd, final String[] cmd,
            final OutputStream output) throws IOException, InterruptedException {
        launcher.launch().cmds(cmd).stdout(output).pwd(pwd).join();
    }

    // package access for test purposes only
    @Restricted(NoExternalUse.class)
    @Nonnull
    abstract FilePath.FileCallable<String> mkCallable(@CheckForNull final TaskListener logOrNull);
}
