package io.jenkins.plugins.extratoolinstallers.installers;

import static hudson.FilePath.TarCompression.GZIP;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.net.URI;
import java.util.Date;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import org.apache.commons.io.input.BoundedInputStream;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

/**
 * Utility class that can download a zip/tar.gz and unpack it.
 */
class AuthenticatedDownloadCallable extends MasterToSlaveFileCallable<Date> {
    @Serial
    private static final long serialVersionUID = 1L;
    @NonNull
    private final URI uri;
    @CheckForNull
    private final String usernameOrNull;
    @CheckForNull
    private final String passwordOrNull;
    @CheckForNull
    private final Long timestampOfLocalContents;
    @NonNull
    private final String nodeName;
    @CheckForNull
    private final TaskListener logOrNull;

    private final boolean fallbackToExistingInstallation;

    /**
     * Passed to {@link FilePath#act(hudson.FilePath.FileCallable)} in order to
     * run
     * {@link #doDownload(ClassicHttpResponse, FilePath, TaskListener, URI, String, String)}
     * on a remote node.
     *
     * @param uri
     *            What to download.
     * @param usernameOrNull
     *            Username to authenticate as, or null for an anonymous
     *            download.
     * @param passwordOrNull
     *            Password for the username, or null for no password.
     * @param timestampOfLocalContents
     *            null for an unconditional download, else the timestamp of what
     *            we have locally.
     * @param nodeName
     *            The name of the node we are downloading onto. Used for logging
     *            purposes only.
     * @param logOrNull
     *            Where to log build progress. Can be null to suppress the
     *            normal running commentary.
     */
    AuthenticatedDownloadCallable(@NonNull URI uri, @CheckForNull String usernameOrNull,
            @CheckForNull String passwordOrNull, @CheckForNull Long timestampOfLocalContents, @NonNull String nodeName,
            @CheckForNull TaskListener logOrNull, boolean fallbackToExistingInstallation) {
        this.uri = uri;
        this.usernameOrNull = usernameOrNull;
        this.passwordOrNull = passwordOrNull;
        this.timestampOfLocalContents = timestampOfLocalContents;
        this.nodeName = nodeName;
        this.logOrNull = logOrNull;
        this.fallbackToExistingInstallation = fallbackToExistingInstallation;
    }

    @Override
    public Date invoke(@NonNull File d, VirtualChannel channel) throws IOException, InterruptedException {
        final FilePath whereToDownloadTo = new FilePath(d);
        return downloadAndUnpack(uri, usernameOrNull, passwordOrNull, timestampOfLocalContents, nodeName,
                whereToDownloadTo, logOrNull, fallbackToExistingInstallation);
    }

    /**
     * Does the download-and-unpack if necessary. The download will be skipped
     * if the remote contents is no newer than the
     * <code>timestampOfLocalContents</code> says. The download will also be
     * skipped if <code>whereToDownloadToOrNull</code> is null.
     *
     * @param uri
     *            What to download.
     * @param usernameOrNull
     *            Username to authenticate as, or null for an anonymous
     *            download.
     * @param passwordOrNull
     *            Password for the username, or null for no password.
     * @param timestampOfLocalContents
     *            null for an unconditional download, else the timestamp of what
     *            we have locally.
     * @param nodeName
     *            The name of the node we are downloading onto. Used for logging
     *            purposes only. Ignored if <code>log</code> is null.
     * @param whereToDownloadToOrNull
     *            The folder where we'll unpack the contents into. Can be null
     *            if all we're doing is testing our ability to contact the
     *            remote server and don't want to do the download for real.
     * @param logOrNull
     *            Where to log build progress. Can be null to suppress the
     *            normal running commentary.
     * @return last-modified date of the remote contents if the contents was
     *         downloaded. null if we did not download but did not error.
     * @throws HttpGetException
     *             if we got a bad response from the webserver.
     * @throws IOException
     *             if we failed to download for other reasons.
     * @throws InterruptedException
     *             if we were interrupted.
     */
    static Date downloadAndUnpack(@NonNull final URI uri, @CheckForNull final String usernameOrNull,
            @CheckForNull final String passwordOrNull, @CheckForNull final Long timestampOfLocalContents,
            @NonNull final String nodeName, @CheckForNull final FilePath whereToDownloadToOrNull,
            @CheckForNull final TaskListener logOrNull, final boolean fallbackToExistingInstallation) throws IOException, InterruptedException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            final HttpClientContext httpClientContext = HttpClientContext.create();
            final HttpUriRequestBase httpRequest;
            if (whereToDownloadToOrNull == null) {
                // we're only validating the URL & credentials.
                httpRequest = new HttpHead(uri);
            } else {
                httpRequest = new HttpGet(uri);
            }
            if (usernameOrNull != null && passwordOrNull != null) {
                setAuthentication(usernameOrNull, passwordOrNull, httpClientContext, uri);
            }
            final Date dateOfLocalContents = timestampOfLocalContents == null ? null : new Date(timestampOfLocalContents);
            final String ifModifiedSince = "If-Modified-Since";
            final String lastModified = "Last-Modified";
            if (dateOfLocalContents != null) {
                final String timestampAsString = DateUtils.formatStandardDate(dateOfLocalContents.toInstant());
                httpRequest.addHeader(ifModifiedSince, timestampAsString);
            }
            try (ClassicHttpResponse httpResponse = httpClient.executeOpen(HttpHost.create(uri), httpRequest, httpClientContext)) {
                final int status = httpResponse.getCode();
                /*
                 * if (logOrNull != null) { final String msg = "HTTP GET of " + uri
                 * + " with request headers of " +
                 * java.util.Arrays.toString(httpGet.getRequestHeaders()) +
                 * " returned response code " + status + " and response headers of "
                 * + java.util.Arrays.toString(httpGet.getResponseHeaders());
                 * logOrNull.getLogger().println(msg); }
                 */
                final Date dateOfRemoteContents;
                switch (status) {
                    case HttpStatus.SC_NOT_MODIFIED:
                        dateOfRemoteContents = null;
                        break;
                    case HttpStatus.SC_OK:
                        final Header lastModifiedResponseHeader = httpResponse.getFirstHeader(lastModified);
                        if (lastModifiedResponseHeader == null) {
                            throw new HttpGetException(uri.toString(), usernameOrNull,
                                    "due to missing " + lastModified + " header value.");
                        }
                        final String lastModifiedStringValue = lastModifiedResponseHeader.getValue();
                        final Date dateFromRemoteServer = DateUtils.toDate(DateUtils.parseStandardDate(lastModifiedStringValue));
                        if (dateFromRemoteServer != null) {
                            if (dateOfLocalContents == null || dateFromRemoteServer.after(dateOfLocalContents)) {
                                dateOfRemoteContents = dateFromRemoteServer;
                            } else {
                                dateOfRemoteContents = null;
                            }
                        } else {
                            throw new HttpGetException(uri.toString(), usernameOrNull, "due to invalid " + lastModified
                                    + " header value, \"" + lastModifiedStringValue + "\".");
                        }
                        break;
                    default:
                        if (fallbackToExistingInstallation && existingToolInstallationAvailable(whereToDownloadToOrNull)) {
                            if (logOrNull != null) {
                                String msg = Messages.AuthenticatedDownloadCallable_fallback_to_existing(status);
                                logOrNull.getLogger().println(msg);
                            }
                            dateOfRemoteContents = null;
                            break;
                        }

                        throw new HttpGetException(uri.toString(), usernameOrNull, status);
                }
                if (whereToDownloadToOrNull != null) {
                    if (dateOfRemoteContents == null) {
                        // we don't want to do the download after all.
                        skipDownload(whereToDownloadToOrNull, logOrNull, uri, nodeName);
                        httpRequest.abort();
                        return null;
                    } else {
                        doDownload(httpResponse, whereToDownloadToOrNull, logOrNull, uri, usernameOrNull, nodeName);
                    }
                }
                return dateOfRemoteContents;
            }
        }
    }

    private static boolean existingToolInstallationAvailable(FilePath whereToDownloadToOrNull) throws IOException, InterruptedException {
        return whereToDownloadToOrNull != null && whereToDownloadToOrNull.exists();
    }

    private static void setAuthentication(@NonNull final String username, @NonNull final String password,
                                          @NonNull final HttpClientContext httpClientContext, @NonNull URI uri) {
        final UsernamePasswordCredentials httpClientCredentials = new UsernamePasswordCredentials(username,
                password.toCharArray());
        final HttpHost targetHost = new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
        final AuthScope scope = new AuthScope(targetHost);
        final BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(scope, httpClientCredentials);
        httpClientContext.setCredentialsProvider(credsProvider);
        final AuthCache authCache = new BasicAuthCache();
        final BasicScheme basicScheme = new BasicScheme();
        basicScheme.initPreemptive(httpClientCredentials);

        authCache.put(targetHost, basicScheme);
        httpClientContext.setAuthCache(authCache);
    }

    private static void skipDownload(@NonNull final FilePath whereToDownloadTo,
            @CheckForNull final TaskListener logOrNull, @NonNull final URI uri, @NonNull final String nodeName) {
        if (logOrNull != null) {
            final String folder = whereToDownloadTo.getRemote();
            final String msg = Messages.AuthenticatedZipExtractionInstaller_download_skipped(uri, folder, nodeName);
            logOrNull.getLogger().println(msg);
        }
    }

    private static void doDownload(@NonNull final ClassicHttpResponse httpResponse, @NonNull final FilePath whereToDownloadTo,
            @CheckForNull final TaskListener logOrNull, @NonNull final URI uri,
            @CheckForNull final String usernameOrNull, @NonNull final String nodeName)
            throws IOException, InterruptedException {
        if (whereToDownloadTo.exists()) {
            whereToDownloadTo.deleteContents();
            if (logOrNull != null) {
                final String folder = whereToDownloadTo.getRemote();
                final String msg = usernameOrNull == null
                        ? Messages.AuthenticatedZipExtractionInstaller_anonymous_download_newer(uri, folder, nodeName)
                        : Messages.AuthenticatedZipExtractionInstaller_authenticated_download_newer(uri, usernameOrNull,
                                folder, nodeName);
                logOrNull.getLogger().println(msg);
            }
        } else {
            whereToDownloadTo.mkdirs();
            if (logOrNull != null) {
                final String folder = whereToDownloadTo.getRemote();
                final String msg = usernameOrNull == null
                        ? Messages.AuthenticatedZipExtractionInstaller_anonymous_download_new(uri, folder, nodeName)
                        : Messages.AuthenticatedZipExtractionInstaller_authenticated_download_new(uri, usernameOrNull,
                                folder, nodeName);
                logOrNull.getLogger().println(msg);
            }
        }
        final boolean isZipNotGzip = uri.getPath().endsWith(".zip");
        final long expectedContentLength = httpResponse.getEntity().getContentLength();
        try (final BoundedInputStream bis = BoundedInputStream.builder().setInputStream(httpResponse.getEntity().getContent()).get()) {
            try {
                if (isZipNotGzip) {
                    whereToDownloadTo.unzipFrom(bis);
                } else {
                    whereToDownloadTo.untarFrom(bis, GZIP);
                }
            } catch (IOException ex) {
                final String msg = Messages.AuthenticatedZipExtractionInstaller_unpack_failed(uri, bis.getCount(),
                        expectedContentLength);
                throw new IOException(msg, ex);
            }
        }
    }

    /**
     * Indicates that we were able to talk to the server but we did not like
     * what it said.
     */
    static class HttpGetException extends IOException {
        @Serial
        private static final long serialVersionUID = 1L;
        @NonNull
        private final String uri;
        @CheckForNull
        private final String usernameOrNull;
        @CheckForNull
        private final Integer httpStatusCodeOrNull;

        private HttpGetException(@NonNull final String uri, @CheckForNull final String usernameOrNull,
                @CheckForNull Integer httpStatusCodeOrNull, @NonNull final String reason, @Nullable Throwable cause) {
            super((usernameOrNull == null ? "Anonymous" : "Authenticated") + " HTTP GET of " + uri
                    + (usernameOrNull == null ? "" : (" as " + usernameOrNull)) + " failed, " + reason, cause);
            this.uri = uri;
            this.usernameOrNull = usernameOrNull;
            this.httpStatusCodeOrNull = httpStatusCodeOrNull;
        }

        HttpGetException(@NonNull final String uri, @CheckForNull final String usernameOrNull, int httpStatusCode) {
            this(uri, usernameOrNull, httpStatusCode, Integer.toString(httpStatusCode), null);
        }

        HttpGetException(@NonNull final String uri, @CheckForNull final String usernameOrNull, @NonNull String reason,
                @Nullable Throwable cause) {
            this(uri, usernameOrNull, null, reason, cause);
        }

        HttpGetException(@NonNull final String uri, @CheckForNull final String usernameOrNull, @NonNull String reason) {
            this(uri, usernameOrNull, null, reason, null);
        }

        @NonNull
        public String getUri() {
            return uri;
        }

        @CheckForNull
        public String getUsername() {
            return usernameOrNull;
        }

        @CheckForNull
        public Integer getHttpStatusCode() {
            return httpStatusCodeOrNull;
        }
    }
}
