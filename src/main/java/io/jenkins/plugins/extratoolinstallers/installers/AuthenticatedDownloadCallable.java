package io.jenkins.plugins.extratoolinstallers.installers;

import static hudson.FilePath.TarCompression.GZIP;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Date;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import org.apache.commons.io.input.CountingInputStream;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

/**
 * Utility class that can download a zip/tar.gz and unpack it.
 */
class AuthenticatedDownloadCallable extends MasterToSlaveFileCallable<Date> {
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
     * {@link #doDownload(HttpRequestBase, FilePath, TaskListener, URI, String, String)}
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
        final CloseableHttpClient httpClient = HttpClients.createDefault();
        final HttpClientContext httpClientContext = HttpClientContext.create();
        final HttpRequestBase httpRequest;
        if (whereToDownloadToOrNull == null) {
            // we're only validating the URL & credentials.
            httpRequest = new HttpHead();
        } else {
            httpRequest = new HttpGet();
        }
        httpRequest.setURI(uri);
        if (usernameOrNull != null) {
            setAuthentication(usernameOrNull, passwordOrNull, httpClient, httpClientContext, uri);
        }
        final Date dateOfLocalContents = timestampOfLocalContents == null ? null : new Date(timestampOfLocalContents);
        final String ifModifiedSince = "If-Modified-Since";
        final String lastModified = "Last-Modified";
        if (dateOfLocalContents != null) {
            final String timestampAsString = DateUtils.formatDate(dateOfLocalContents);
            httpRequest.addHeader(ifModifiedSince, timestampAsString);
        }
        try (CloseableHttpResponse httpResponse = httpClient.execute(httpRequest, httpClientContext)) {
            final int status = httpResponse.getStatusLine().getStatusCode();
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
                case HttpStatus.SC_NOT_MODIFIED :
                    dateOfRemoteContents = null;
                    break;
                case HttpStatus.SC_OK :
                    final Header lastModifiedResponseHeader = httpResponse.getFirstHeader(lastModified);
                    if (lastModifiedResponseHeader == null) {
                        throw new HttpGetException(uri.toString(), usernameOrNull,
                                "due to missing " + lastModified + " header value.");
                    }
                    final String lastModifiedStringValue = lastModifiedResponseHeader.getValue();
                    final Date dateFromRemoteServer = DateUtils.parseDate(lastModifiedStringValue);
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
                default :
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

    private static boolean existingToolInstallationAvailable(FilePath whereToDownloadToOrNull) throws IOException, InterruptedException {
        return whereToDownloadToOrNull != null && whereToDownloadToOrNull.exists();
    }

    private static void setAuthentication(@CheckForNull final String usernameOrNull,
            @CheckForNull final String passwordOrNull, @NonNull final CloseableHttpClient client,
            @NonNull final HttpClientContext httpClientContext, @NonNull URI uri) {
        final UsernamePasswordCredentials httpClientCredentials = new UsernamePasswordCredentials(usernameOrNull,
                passwordOrNull);
        final AuthScope scope = new AuthScope(uri.getHost(), uri.getPort());
        final CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(scope, httpClientCredentials);
        httpClientContext.setCredentialsProvider(credsProvider);
        final AuthCache authCache = new BasicAuthCache();
        authCache.put(
                new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()),
                new BasicScheme());
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

    private static void doDownload(@NonNull final CloseableHttpResponse httpResponse, @NonNull final FilePath whereToDownloadTo,
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
        try (final CountingInputStream cis = new CountingInputStream(httpResponse.getEntity().getContent())) {
            try {
                if (isZipNotGzip) {
                    whereToDownloadTo.unzipFrom(cis);
                } else {
                    whereToDownloadTo.untarFrom(cis, GZIP);
                }
            } catch (IOException ex) {
                final String msg = Messages.AuthenticatedZipExtractionInstaller_unpack_failed(uri, cis.getByteCount(),
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
