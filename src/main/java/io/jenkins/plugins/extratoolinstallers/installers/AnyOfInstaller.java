package io.jenkins.plugins.extratoolinstallers.installers;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.synopsys.arc.jenkinsci.plugins.extratoolinstallers.utils.ExtraToolInstallersException;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolInstallerDescriptor;
import hudson.util.FormValidation;

/**
 * Installs tools using "any of" the installation methods provided. The
 * installation is deemed a success upon any success, ignoring any earlier
 * failures.
 */
public class AnyOfInstaller extends ToolInstaller {
    /**
     * The list of installers we will attempt. Cannot be empty for this to
     * be valid.
     */
    @CheckForNull
    private /* almost final */ InstallSourceProperty installers;

    /**
     * The number of times we will attempt each installer before moving onto the
     * next in the list.
     */
    private /* almost final */ int attemptsPerInstaller;

    /**
     * The number of times we will attempt the list as a whole. Must not be less
     * than one.
     */
    private /* almost final */ int attemptsOfWholeList;

    /**
     * Default constructor.
     */
    @DataBoundConstructor
    public AnyOfInstaller() {
        // we never have a label ourselves; we only ever have labels in our
        // installers.
        super(null);
    }

    /**
     * The list of installers we will attempt. Cannot be empty for this installer to be
     * valid.
     * 
     * @return Our installers.
     */
    @CheckForNull
    public InstallSourceProperty getInstallers() {
        return installers;
    }

    /**
     * Sets {@link #getInstallers()}.
     * 
     * @param installers The new value.
     */
    @DataBoundSetter
    public void setInstallers(@Nullable final InstallSourceProperty installers) {
        this.installers = installers;
        if (super.tool != null) {
            installers.setTool(super.tool);
        }
    }

    /**
     * The number of times we will attempt each installer before moving onto the
     * next in the list. Will always be one or more.
     * 
     * @return The value set by {@link #setAttemptsPerInstaller(int)} if that
     *         was 1 or more, else 1.
     */
    public int getAttemptsPerInstaller() {
        return Math.max(1, attemptsPerInstaller);
    }

    /**
     * Sets {@link #getAttemptsPerInstaller()}.
     * 
     * @param attemptsPerInstaller The new value.
     */
    @DataBoundSetter
    public void setAttemptsPerInstaller(final int attemptsPerInstaller) {
        this.attemptsPerInstaller = attemptsPerInstaller;
    }

    /**
     * The number of times we will attempt each installer before moving onto the
     * next in the list. Will always return one or more.
     * 
     * @return The value set by {@link #setAttemptsOfWholeList(int)} if that was
     *         1 or more, else 1.
     */
    public int getAttemptsOfWholeList() {
        return Math.max(1, attemptsOfWholeList);
    }

    /**
     * Sets {@link #getAttemptsOfWholeList()}.
     * 
     * @param attemptsOfWholeList The new value.
     */
    @DataBoundSetter
    public void setAttemptsOfWholeList(final int attemptsOfWholeList) {
        this.attemptsOfWholeList = attemptsOfWholeList;
    }

    @Override
    protected void setTool(final ToolInstallation t) {
        super.setTool(t);
        if (installers != null) {
            installers.setTool(t);
        }
    }

    @Override
    public boolean appliesTo(final Node node) {
        // We "apply" if any of our installers apply.
        // We have no separate existence of our own.
        final List<? extends ToolInstaller> ourInstallers = getOurInstallers();
        for (final ToolInstaller installer : ourInstallers) {
            if (installer.appliesTo(node)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public FilePath performInstallation(final ToolInstallation tool, final Node node, final TaskListener log)
            throws IOException, InterruptedException {
        // Work out what we are going to do
        final List<? extends ToolInstaller> allDefinedInstallers = getOurInstallers();
        final int numberOfConfiguredInstallers = allDefinedInstallers.size();
        final Map<Integer, ToolInstaller> allApplicableInstallersByIndex = calcInstallersThatApplyToNode(node,
                allDefinedInstallers);
        final Map<Integer, String> allApplicableInstallerNamesByIndex = calcInstallerDisplayNames(
                allApplicableInstallersByIndex);
        final int maxWholeListAttempts = getAttemptsOfWholeList();
        final int maxAttemptsPerInstaller = getAttemptsPerInstaller();
        // Now loop through all attempts until either one works and we return
        // or until we run out of tries, in which case we throw the last
        // exception
        Exception lastExceptionEncountered = null;
        for (int wholeListAttempt = 1; wholeListAttempt <= maxWholeListAttempts; wholeListAttempt++) {
            for (final Map.Entry<Integer, ToolInstaller> entry : allApplicableInstallersByIndex.entrySet()) {
                final ToolInstaller installer = entry.getValue();
                final Integer indexOfConfiguredInstaller = entry.getKey();
                for (int installerAttempt = 1; installerAttempt <= maxAttemptsPerInstaller; installerAttempt++) {
                    try {
                        final FilePath result = installer.performInstallation(tool, node, log);
                        return result; // success
                    } catch (IOException | RuntimeException ex) {
                        lastExceptionEncountered = ex;
                        final String displayNameOfThisInstaller = allApplicableInstallerNamesByIndex
                                .get(indexOfConfiguredInstaller);
                        final String whatToReport = ex.toString();
                        logAttempt(log, whatToReport, wholeListAttempt, maxWholeListAttempts,
                                numberOfConfiguredInstallers, indexOfConfiguredInstaller, displayNameOfThisInstaller,
                                maxAttemptsPerInstaller, installerAttempt);
                    }
                }
            }
        }
        throw new ExtraToolInstallersException(this, Messages.AnyOfInstaller_all_failed(), lastExceptionEncountered);
    }

    @NonNull
    private List<? extends ToolInstaller> getOurInstallers() {
        if (installers == null || installers.installers == null) {
            return Collections.emptyList();
        }
        return installers.installers.getAll(ToolInstaller.class);
    }

    /**
     * Determines which installers <code>appliesTo</code> the given node.
     * 
     * @param node
     *            The node
     * @param installers
     *            The installers to be considered
     * @return A Map of installers, indexed by where they were in the list
     *         (starting from 1).
     */
    private static Map<Integer, ToolInstaller> calcInstallersThatApplyToNode(final Node node,
            final List<? extends ToolInstaller> installers) {
        final Map<Integer, ToolInstaller> allApplicableInstallersByIndex = new LinkedHashMap<>(installers.size());
        int index = 0;
        for (final ToolInstaller installer : installers) {
            index++;
            if (installer.appliesTo(node)) {
                allApplicableInstallersByIndex.put(index, installer);
            }
        }
        return allApplicableInstallersByIndex;
    }

    /**
     * Determines the human-readable names of the installers.
     * 
     * @param installersByIndex
     *            Map of installers.
     * @return Map of human-readable names of the installers, indexed by the
     *         same key as the installer was.
     */
    private static <K> Map<K, String> calcInstallerDisplayNames(final Map<K, ToolInstaller> installersByIndex) {
        final Map<K, String> installerNamesByIndex = new IdentityHashMap<>(installersByIndex.size());
        for (final Map.Entry<K, ToolInstaller> entry : installersByIndex.entrySet()) {
            final ToolInstaller installer = entry.getValue();
            final K index = entry.getKey();
            final String installerName = installer.getDescriptor().getDisplayName();
            installerNamesByIndex.put(index, installerName);
        }
        return installerNamesByIndex;
    }

    /**
     * Logs a message to the build log, indicating the point in our proceedings
     * that the message relates to, using as little text as possible to do it.
     */
    private static void logAttempt(final TaskListener log, final String whatToReport, final int wholeListAttempt,
            final int maxWholeListAttempts, final int numberOfConfiguredOfInstallers,
            final Integer indexOfConfiguredInstaller, final String nameOfConfiguredInstaller,
            final int maxAttemptsPerInstaller, final int installerAttempt) {
        final String msg;
        // Select the best localized message given our config - if we're not
        // doing multiple loops then there's no point saying we're on loop 1 of
        // 1 etc.
        if (maxWholeListAttempts > 1) {
            // We have multiple loops, so we need to use "loops" messages not
            // "1loop" variants so we that our log message specifies which
            // attempt that was.
            if (numberOfConfiguredOfInstallers > 1) {
                // We have multiple configured installers, so we need to use
                // "installers" messages not "1installer" variants so that our
                // log message specifies which installer that was.
                if (maxAttemptsPerInstaller > 1) {
                    // We have multiple attempts per installer, so we need to
                    // use "attempts" messages not "1attempt" variants so that
                    // our log message specifies which attempt it was.
                    msg = Messages.AnyOfInstaller_loops_installers_attempts(wholeListAttempt, maxWholeListAttempts,
                            indexOfConfiguredInstaller, numberOfConfiguredOfInstallers, nameOfConfiguredInstaller,
                            installerAttempt, maxAttemptsPerInstaller, whatToReport);
                } else {
                    msg = Messages.AnyOfInstaller_loops_installers_1attempt(wholeListAttempt, maxWholeListAttempts,
                            indexOfConfiguredInstaller, numberOfConfiguredOfInstallers, nameOfConfiguredInstaller,
                            installerAttempt, maxAttemptsPerInstaller, whatToReport);
                }
            } else {
                if (maxAttemptsPerInstaller > 1) {
                    msg = Messages.AnyOfInstaller_loops_1installer_attempts(wholeListAttempt, maxWholeListAttempts,
                            indexOfConfiguredInstaller, numberOfConfiguredOfInstallers, nameOfConfiguredInstaller,
                            installerAttempt, maxAttemptsPerInstaller, whatToReport);
                } else {
                    msg = Messages.AnyOfInstaller_loops_1installer_1attempt(wholeListAttempt, maxWholeListAttempts,
                            indexOfConfiguredInstaller, numberOfConfiguredOfInstallers, nameOfConfiguredInstaller,
                            installerAttempt, maxAttemptsPerInstaller, whatToReport);
                }
            }
        } else {
            if (numberOfConfiguredOfInstallers > 1) {
                if (maxAttemptsPerInstaller > 1) {
                    msg = Messages.AnyOfInstaller_1loop_installers_attempts(wholeListAttempt, maxWholeListAttempts,
                            indexOfConfiguredInstaller, numberOfConfiguredOfInstallers, nameOfConfiguredInstaller,
                            installerAttempt, maxAttemptsPerInstaller, whatToReport);
                } else {
                    msg = Messages.AnyOfInstaller_1loop_installers_1attempt(wholeListAttempt, maxWholeListAttempts,
                            indexOfConfiguredInstaller, numberOfConfiguredOfInstallers, nameOfConfiguredInstaller,
                            installerAttempt, maxAttemptsPerInstaller, whatToReport);
                }
            } else {
                if (maxAttemptsPerInstaller > 1) {
                    msg = Messages.AnyOfInstaller_1loop_1installer_attempts(wholeListAttempt, maxWholeListAttempts,
                            indexOfConfiguredInstaller, numberOfConfiguredOfInstallers, nameOfConfiguredInstaller,
                            installerAttempt, maxAttemptsPerInstaller, whatToReport);
                } else {
                    msg = Messages.AnyOfInstaller_1loop_1installer_1attempt(wholeListAttempt, maxWholeListAttempts,
                            indexOfConfiguredInstaller, numberOfConfiguredOfInstallers, nameOfConfiguredInstaller,
                            installerAttempt, maxAttemptsPerInstaller, whatToReport);
                }
            }
        }
        // now log it
        final PrintStream output = log.getLogger();
        output.println(msg);
    }

    /**
     * Descriptor for the {@link AnyOfInstaller}.
     */
    @Extension @Symbol("anyOf")
    public static class DescriptorImpl extends ToolInstallerDescriptor<AnyOfInstaller> {
        @Override
        public String getDisplayName() {
            return Messages.AnyOfInstaller_DescriptorImpl_displayName();
        }

        public FormValidation doCheckAttemptsPerInstaller(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }

        public FormValidation doCheckAttemptsOfWholeList(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }
    }
}
