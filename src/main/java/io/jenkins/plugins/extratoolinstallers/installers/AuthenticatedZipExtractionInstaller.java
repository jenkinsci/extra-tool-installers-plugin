package io.jenkins.plugins.extratoolinstallers.installers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.PasswordCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.UsernameCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.HostnameRequirement;
import com.synopsys.arc.jenkinsci.plugins.extratoolinstallers.utils.ExtraToolInstallersException;

import hudson.Extension;
import hudson.FilePath;
import hudson.Functions;
import hudson.Util;
import hudson.model.ItemGroup;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolInstallerDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;

/**
 * A {@link ToolInstaller} that downloads a zip or tar.gz and unpacks it in
 * order to install a tool. If the tool is already present, it will only be
 * re-downloaded/re-unpacked if the URL is newer than the existing content. The
 * download supports HTTP basic authentication.
 */
public class AuthenticatedZipExtractionInstaller extends ToolInstaller {
    @CheckForNull
    private String url;

    @CheckForNull
    private String credentialsId;

    @CheckForNull
    private String subdir;

    /**
     * Constructor that sets mandatory fields.
     * 
     * @param label The {@link ToolInstaller#getLabel()}.
     */
    @DataBoundConstructor
    public AuthenticatedZipExtractionInstaller(String label) {
        super(label);
    }

    /**
     * URL of a zip/tar.gz file which should be downloaded and unpacked if the
     * tool is missing or out of date.
     * 
     * @return URL, or null if none has been set.
     */
    @CheckForNull
    public String getUrl() {
        return Util.fixEmpty(url);
    }

    /**
     * Sets {@link #getUrl()}.
     * 
     * @param url
     *            New value.
     */
    @DataBoundSetter
    public void setUrl(@Nullable String url) {
        this.url = Util.fixEmpty(url);
    }

    /**
     * ID of the credentials to use when doing the download.
     * 
     * @return The credentials ID, or null if none has been set.
     */
    @CheckForNull
    public String getCredentialsId() {
        return Util.fixEmpty(credentialsId);
    }

    /**
     * Sets {@link #getCredentialsId()}.
     * 
     * @param credentialsId
     *            New value.
     */
    @DataBoundSetter
    public void setCredentialsId(@Nullable String credentialsId) {
        this.credentialsId = Util.fixEmpty(credentialsId);
    }

    /**
     * Subdirectory within the zip/tar.gz where the tool's binaries are located.
     * It is this folder that's added to the path (etc). Can be null/empty if
     * the binaries are at the base of the zip/tar.gz.
     * 
     * @return The subdirectory, or null if no subdirectory has been set.
     */
    @CheckForNull
    public String getSubdir() {
        return Util.fixEmpty(subdir);
    }

    /**
     * Sets {@link #getSubdir()}.
     * 
     * @param subdir
     *            New value.
     */
    @DataBoundSetter
    public void setSubdir(@Nullable String subdir) {
        this.subdir = Util.fixEmpty(subdir);
    }

    @Override
    public FilePath performInstallation(@NonNull ToolInstallation tool, @NonNull Node node,
            @CheckForNull TaskListener log) throws IOException, InterruptedException {
        final String url = getUrl();
        final URI uri;
        final String urlHost;
        try {
            uri = new URI(url, false);
            urlHost = uri.getHost();
        } catch (URIException ex) {
            throw new ExtraToolInstallersException(this, Messages.AuthenticatedZipExtractionInstaller_malformed_url(),
                    ex);
        }
        final StandardCredentials credentialsOrNull = lookupConfiguredCredentials(urlHost);
        final String usernameOrNull;
        final String passwordOrNull;
        if (credentialsOrNull == null) {
            usernameOrNull = null;
            passwordOrNull = null;
        } else {
            usernameOrNull = getUsernameFromCredentials(credentialsOrNull);
            passwordOrNull = getPasswordFromCredentials(credentialsOrNull);
        }
        final FilePath dir = preferredLocation(tool, node);
        final Long timestampOfLocalContents;
        final FilePath timestamp = dir.child(".timestamp");
        if (timestamp.exists()) {
            timestampOfLocalContents = timestamp.lastModified();
        } else {
            timestampOfLocalContents = null;
        }
        final String nodeName = node.getDisplayName();
        /*
         * if (log != null) { log.getLogger()
         * .println(this.getClass().getSimpleName() + ": credentialsId=" +
         * credentialsIdOrNull + ", user=" + usernameOrNull + ", pwd=" +
         * passwordOrNull + ", uri=" + uri.toString() +
         * ", timestampOfLocalContents=" + timestampOfLocalContents +
         * ", nodeName=" + nodeName); }
         */
        final Date timestampOfRemoteResource = downloadOnNodeWithFallbackToMaster(uri, timestampOfLocalContents,
                usernameOrNull, passwordOrNull, nodeName, dir, log);
        if (timestampOfRemoteResource != null) {
            dir.act(new ChmodRecAPlusX());
            timestamp.touch(timestampOfRemoteResource.getTime());
        }
        final String subdirOrNull = getSubdir();
        if (subdirOrNull == null) {
            return dir;
        } else {
            return dir.child(subdirOrNull);
        }
    }

    private Date downloadOnNodeWithFallbackToMaster(@NonNull final URI uri,
            @CheckForNull final Long timestampOfLocalContents, @CheckForNull final String usernameOrNull,
            @CheckForNull final String passwordOrNull, @NonNull final String nodeName, @NonNull final FilePath dir,
            @CheckForNull final TaskListener logOrNull) throws IOException, InterruptedException {
        if (dir.isRemote()) {
            /*
             * if (log != null) {
             * log.getLogger().println("Trying download from remote node " +
             * nodeName); }
             */
            try {
                final Date timestampOfRemoteResource = downloadOnRemoteNode(uri, timestampOfLocalContents,
                        usernameOrNull, passwordOrNull, dir, logOrNull, nodeName);
                /*
                 * if (log != null) { if (timestampOfRemoteResource != null) {
                 * log.getLogger().println("Download from remote node " +
                 * nodeName + " successful, timestamp of resource is now " +
                 * timestampOfRemoteResource.getTime() + " (aka " +
                 * timestampOfRemoteResource + ")"); } else {
                 * log.getLogger().println("Download from remote node " +
                 * nodeName + " skipped"); } }
                 */
                return timestampOfRemoteResource;
            } catch (AuthenticatedDownloadCallable.HttpGetException | InterruptedException ex) {
                // No point retrying from master. The failure was not caused by
                // our inability to talk to URI.
                throw ex;
            } catch (IOException ex) {
                if (logOrNull != null) {
                    Functions.printStackTrace(ex,
                            logOrNull.error("Failed to download " + uri + " from agent; will try from master instead"));
                }
            }
        }
        /*
         * if (log != null) { log.getLogger().println(
         * "Trying download from master, piping data to node " + nodeName); }
         */
        final Date timestampOfRemoteResource = downloadOnFromMaster(uri, timestampOfLocalContents, usernameOrNull,
                passwordOrNull, dir, logOrNull, nodeName);
        /*
         * if (log != null) { if (timestampOfRemoteResource != null) {
         * log.getLogger() .println("Download from master to node " + nodeName +
         * " successful, timestamp of resource is now " +
         * timestampOfRemoteResource.getTime() + " (aka " +
         * timestampOfRemoteResource + ")"); } else {
         * log.getLogger().println("Download from master to node " + nodeName +
         * " skipped"); } }
         */
        return timestampOfRemoteResource;
    }

    protected StandardCredentials lookupConfiguredCredentials(@CheckForNull final String urlHostOrNullOrEmpty)
            throws ExtraToolInstallersException {
        final String credentialsIdOrNull = getCredentialsId();
        if (credentialsIdOrNull == null) {
            return null;
        }
        final StandardCredentials credentialsOrNull = getCredentialsOrNull(credentialsIdOrNull, urlHostOrNullOrEmpty);
        if (credentialsOrNull == null) {
            throw new ExtraToolInstallersException(this,
                    Messages.AuthenticatedZipExtractionInstaller_invalid_credentials(credentialsIdOrNull));
        }
        return credentialsOrNull;
    }

    protected Date downloadOnFromMaster(@NonNull final URI uri, @CheckForNull final Long timestampOfLocalContents,
            @CheckForNull final String usernameOrNull, @CheckForNull final String passwordOrNull,
            @NonNull final FilePath dir, @CheckForNull final TaskListener logOrNull, @NonNull final String nodeName)
            throws IOException, InterruptedException {
        final Date timestampOfRemoteResource = AuthenticatedDownloadCallable.downloadAndUnpack(uri, usernameOrNull,
                passwordOrNull, timestampOfLocalContents, nodeName, dir, logOrNull);
        return timestampOfRemoteResource;
    }

    protected Date downloadOnRemoteNode(@NonNull final URI uri, @CheckForNull final Long timestampOfLocalContents,
            @CheckForNull final String usernameOrNull, @CheckForNull final String passwordOrNull,
            @NonNull final FilePath dir, @CheckForNull final TaskListener logOrNull, @NonNull final String nodeName)
            throws IOException, InterruptedException {
        final AuthenticatedDownloadCallable nodeOperation = new AuthenticatedDownloadCallable(uri, usernameOrNull,
                passwordOrNull, timestampOfLocalContents, nodeName, logOrNull);
        final Date timestampOfRemoteResource = dir.act(nodeOperation);
        return timestampOfRemoteResource;
    }

    /**
     * Looks up credentials by ID, ensuring they're valid for the specified host
     */
    private static @CheckForNull StandardCredentials getCredentialsOrNull(@NonNull final String credentialsId,
            @CheckForNull final String urlHostOrNullOrEmpty) {
        final List<DomainRequirement> forOurUrl = getDomainRequirements(urlHostOrNullOrEmpty);
        final ItemGroup<?> allOfJenkins = Jenkins.getInstanceOrNull();
        final CredentialsMatcher onlyOurCredentials = CredentialsMatchers.allOf(CREDENTIAL_TYPES_WE_CAN_HANDLE,
                CredentialsMatchers.withId(credentialsId));
        final List<StandardCredentials> allJenkinsCredentialsForOurUrl = CredentialsProvider
                .lookupCredentials(StandardCredentials.class, allOfJenkins, ACL.SYSTEM, forOurUrl);
        final StandardCredentials ourCredentialsOrNull = CredentialsMatchers.firstOrNull(allJenkinsCredentialsForOurUrl,
                onlyOurCredentials);
        return ourCredentialsOrNull;
    }

    /** Extracts the username from any credential type we support. */
    private static @CheckForNull String getUsernameFromCredentials(@NonNull StandardCredentials credentials) {
        if (credentials instanceof UsernameCredentials) {
            final UsernameCredentials userCreds = (UsernameCredentials) credentials;
            return userCreds.getUsername();
        } else {
            return null;
        }
    }

    /** Extracts the password from any credential type we support. */
    private static @CheckForNull String getPasswordFromCredentials(@NonNull StandardCredentials credentials) {
        if (credentials instanceof PasswordCredentials) {
            final PasswordCredentials pwdCreds = (PasswordCredentials) credentials;
            return Secret.toString(pwdCreds.getPassword());
        } else {
            return null;
        }
    }

    /**
     * Defines what credential types are supported by
     * {@link #getUsernameFromCredentials(StandardCredentials)} and
     * {@link #getPasswordFromCredentials(StandardCredentials)}
     */
    private static final CredentialsMatcher CREDENTIAL_TYPES_WE_CAN_HANDLE = CredentialsMatchers.anyOf(
            CredentialsMatchers.instanceOf(PasswordCredentials.class),
            CredentialsMatchers.instanceOf(UsernameCredentials.class));

    /** Limits credentials to those available to our URL's server. */
    private static @NonNull List<DomainRequirement> getDomainRequirements(
            @CheckForNull final String urlHostOrNullOrEmpty) {
        if (Util.fixEmpty(urlHostOrNullOrEmpty) != null) {
            return Collections.singletonList(new HostnameRequirement(urlHostOrNullOrEmpty));
        } else {
            return Collections.emptyList();
        }
    }

    /** Copy of hudson.tools.ZipExtractionInstaller.ChmodRecAPlusX */
    static class ChmodRecAPlusX extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1L;

        @Override
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

    /**
     * Descriptor for the {@link AuthenticatedZipExtractionInstaller}.
     */
    @Extension @Symbol("authenticatedzip")
    public static class DescriptorImpl extends ToolInstallerDescriptor<AuthenticatedZipExtractionInstaller> {
        @Override
        public String getDisplayName() {
            return Messages.AuthenticatedZipExtractionInstaller_DescriptorImpl_displayName();
        }

        /* List credentials that can be used on the specified URL */
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter String credentialsId, @QueryParameter String url) {
            /*
             * System.out.println("doFillCredentialsIdItems(" + item + "," +
             * credentialsId + "," + url + ")");
             */
            final StandardListBoxModel result = new StandardListBoxModel();
            if (hasPermissionToConfigure()) {
                result.includeEmptyValue();
                String urlHostOrNullOrEmpty = null;
                try {
                    final String urlString = Util.fixEmpty(url);
                    if (urlString != null) {
                        final URI uri = new URI(urlString, false);
                        urlHostOrNullOrEmpty = uri.getHost();
                    }
                } catch (URIException ex) {
                    /*
                     * System.out.println("  url = invalid, ex=" +
                     * ex.toString());
                     */
                    // suppress exception as url is validated elsewhere
                }
                /* System.out.println("  urlHost = " + urlHost); */
                final ItemGroup<?> allOfJenkins = Jenkins.getInstanceOrNull();
                final List<DomainRequirement> domainRequirements = getDomainRequirements(urlHostOrNullOrEmpty);
                result.includeMatchingAs(ACL.SYSTEM, allOfJenkins, StandardCredentials.class, domainRequirements,
                        CREDENTIAL_TYPES_WE_CAN_HANDLE);
            }
            result.includeCurrentValue(credentialsId);
            /* System.out.println("  result = " + result); */
            return result;
        }

        /*
         * Validates our URL+Credentials, but only returns an error if there's a
         * problem with the Credentials.
         */
        @RequirePOST // validation will expose credentials to the url
        public FormValidation doCheckCredentialsId(@QueryParameter String value, @QueryParameter String url) {
            /*
             * System.out.println("doCheckCredentialsId(" + item + "," + value +
             * "," + url + ")");
             */
            final String urlOrNull = Util.fixEmpty(url);
            final String credentialsIdOrNull = Util.fixEmpty(value);
            return checkUrlAndCredentialsId(false, credentialsIdOrNull, urlOrNull);
        }

        /*
         * Validates our URL+Credentials, but only returns an error if there's a
         * problem with the URL.
         */
        @RequirePOST // validation will expose credentials to the url
        public FormValidation doCheckUrl(@QueryParameter String credentialsId, @QueryParameter String value) {
            /*
             * System.out.println("doCheckUrl(" + item + "," + credentialsId +
             * "," + value + ")");
             */
            final String urlOrNull = Util.fixEmpty(value);
            final String credentialsIdOrNull = Util.fixEmpty(credentialsId);
            return checkUrlAndCredentialsId(true, credentialsIdOrNull, urlOrNull);
        }

        /**
         * Validates the URL + Credentials as a pair.
         * 
         * @param checkUrl
         *            If true then we should only return URL problems and just
         *            return {@link FormValidation#ok()} if there's a credential
         *            problem. If false then we should only return credential
         *            problems and just return {@link FormValidation#ok()} if
         *            there's a URL problem.
         * @param credentialsIdOrNull
         *            The credentials to check.
         * @param urlOrNull
         *            The URL to check. If we have non-null credentials then
         *            we'll verify the URL is reachable with those credentials;
         *            if we have null credentials then we'll verify that the URL
         *            is reachable via anonymous download.
         * @return If <code>checkUrl</code> is true then we return what's wrong
         *         (if anything) with the URL. If <code>checkUrl</code> is false
         *         then we return what's wrong (if anything) with the
         *         credentials.
         */
        private static @NonNull FormValidation checkUrlAndCredentialsId(final boolean checkUrl,
                @CheckForNull final String credentialsIdOrNull, @CheckForNull final String urlOrNull) {
            if (!hasPermissionToConfigure()) {
                /*
                 * System.out.println(
                 * "checkUrlAndCredentialsId:  NOT hasPermissionToConfigure");
                 */
                return FormValidation.ok();
            }
            if (urlOrNull == null) {
                /*
                 * System.out.println("checkUrlAndCredentialsId:  url = null");
                 */
                return urlProblem(checkUrl, FormValidation.validateRequired(""));
            }
            final URI uri;
            final String urlHostOrNullOrEmpty;
            try {
                uri = new URI(urlOrNull, false);
                urlHostOrNullOrEmpty = uri.getHost();
            } catch (URIException ex) {
                /*
                 * System.out.println(
                 * "checkUrlAndCredentialsId:  url = invalid, ex=" +
                 * ex.toString());
                 */
                return urlProblem(checkUrl,
                        FormValidation.error(ex, Messages.AuthenticatedZipExtractionInstaller_malformed_url()));
            }
            final String usernameOrNull;
            final String passwordOrNull;
            /*
             * System.out.println(
             * "checkUrlAndCredentialsId:  credentialsIdOrNull = " +
             * credentialsIdOrNull);
             */
            if (credentialsIdOrNull == null) {
                usernameOrNull = null;
                passwordOrNull = null;
            } else {
                final StandardCredentials credentialsOrNull = getCredentialsOrNull(credentialsIdOrNull,
                        urlHostOrNullOrEmpty);
                /*
                 * System.out.println(
                 * "checkUrlAndCredentialsId:  credentialsOrNull = " +
                 * credentialsOrNull);
                 */
                if (credentialsOrNull == null) {
                    return credentialProblem(checkUrl, FormValidation.error(
                            Messages.AuthenticatedZipExtractionInstaller_invalid_credentials(credentialsIdOrNull)));
                }
                usernameOrNull = getUsernameFromCredentials(credentialsOrNull);
                passwordOrNull = getPasswordFromCredentials(credentialsOrNull);
            }
            try {
                final Long timestampOfLocalContents = null;
                final String nodeName = "";
                final FilePath whereToDownloadToOrNull = null;
                final TaskListener log = null;
                /*
                 * System.out.println(
                 * "checkUrlAndCredentialsId:  DownloadIfNecessary.payload(" +
                 * uri + "," + usernameOrNull + "," + passwordOrNull + "," +
                 * timestampOfLocalContents + "," + nodeName + "," +
                 * whereToDownloadToOrNull + "," + log + ")");
                 */
                AuthenticatedDownloadCallable.downloadAndUnpack(uri, usernameOrNull, passwordOrNull,
                        timestampOfLocalContents, nodeName, whereToDownloadToOrNull, log);
            } catch (AuthenticatedDownloadCallable.HttpGetException ex) {
                final Integer httpStatusCodeOrNull = ex.getHttpStatusCode();
                if (httpStatusCodeOrNull != null) {
                    final int httpStatusCode = httpStatusCodeOrNull.intValue();
                    if (httpStatusCode == HttpStatus.SC_UNAUTHORIZED) {
                        if (usernameOrNull == null) {
                            return credentialProblem(checkUrl, FormValidation.error(ex,
                                    Messages.AuthenticatedZipExtractionInstaller_credentials_required()));
                        } else {
                            return credentialProblem(checkUrl, FormValidation.error(ex,
                                    Messages.AuthenticatedZipExtractionInstaller_credentials_rejected(usernameOrNull)));
                        }
                    }
                    if (httpStatusCode == HttpStatus.SC_NOT_FOUND) {
                        return credentialProblem(checkUrl, FormValidation.error(ex,
                                Messages.AuthenticatedZipExtractionInstaller_404_http_response_from_server()));
                    }
                }
                return urlProblem(checkUrl, FormValidation.error(ex,
                        Messages.AuthenticatedZipExtractionInstaller_bad_http_response_from_server(ex.getMessage())));
            } catch (IOException | InterruptedException | RuntimeException ex) {
                return urlProblem(checkUrl,
                        FormValidation.error(ex, Messages.AuthenticatedZipExtractionInstaller_could_not_connect(ex.toString())));
            }
            return FormValidation.ok();
        }

        private static @NonNull FormValidation urlProblem(final boolean checkUrlNotCredentials,
                @NonNull final FormValidation problemWithUrl) {
            return checkUrlNotCredentials ? problemWithUrl : FormValidation.ok();
        }

        private static @NonNull FormValidation credentialProblem(final boolean checkUrlNotCredentials,
                @NonNull final FormValidation problemWithCredentials) {
            return checkUrlNotCredentials ? FormValidation.ok() : problemWithCredentials;
        }

        private static boolean hasPermissionToConfigure() {
            return Jenkins.get().hasPermission(Jenkins.ADMINISTER);
        }
    }
}
