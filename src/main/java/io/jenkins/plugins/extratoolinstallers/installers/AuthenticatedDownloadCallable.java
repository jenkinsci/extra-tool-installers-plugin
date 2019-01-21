package io.jenkins.plugins.extratoolinstallers.installers;

import static hudson.FilePath.TarCompression.GZIP;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.util.DateParseException;
import org.apache.commons.httpclient.util.DateUtil;
import org.apache.commons.io.input.CountingInputStream;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

/**
 * Utility class that can download a zip/tar.gz and unpack it.
 */
class AuthenticatedDownloadCallable extends MasterToSlaveFileCallable<Date> {
    private static final long serialVersionUID = 1L;
    @Nonnull
    private final URI uri;
    @CheckForNull
    private final String usernameOrNull;
    @CheckForNull
    private final String passwordOrNull;
    @CheckForNull
    private final Long timestampOfLocalContents;
    @Nonnull
    private final String nodeName;
    @CheckForNull
    private final TaskListener logOrNull;

    /**
     * Passed to {@link FilePath#act(hudson.FilePath.FileCallable)} in order to
     * run
     * {@link #doDownload(GetMethod, FilePath, TaskListener, URI, String, String)}
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
    AuthenticatedDownloadCallable(@Nonnull URI uri, @CheckForNull String usernameOrNull,
            @CheckForNull String passwordOrNull, @CheckForNull Long timestampOfLocalContents, @Nonnull String nodeName,
            @CheckForNull TaskListener logOrNull) {
        this.uri = uri;
        this.usernameOrNull = usernameOrNull;
        this.passwordOrNull = passwordOrNull;
        this.timestampOfLocalContents = timestampOfLocalContents;
        this.nodeName = nodeName;
        this.logOrNull = logOrNull;
    }

    public Date invoke(@Nonnull File d, VirtualChannel channel) throws IOException, InterruptedException {
        final FilePath whereToDownloadTo = new FilePath(d);
        return downloadAndUnpack(uri, usernameOrNull, passwordOrNull, timestampOfLocalContents, nodeName,
                whereToDownloadTo, logOrNull);
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
    static Date downloadAndUnpack(@Nonnull final URI uri, @CheckForNull final String usernameOrNull,
            @CheckForNull final String passwordOrNull, @CheckForNull final Long timestampOfLocalContents,
            @Nonnull final String nodeName, @CheckForNull final FilePath whereToDownloadToOrNull,
            @CheckForNull final TaskListener logOrNull) throws IOException, InterruptedException {
        final HttpClient client = new HttpClient();
        final HttpMethodBase httpRequest;
        if (whereToDownloadToOrNull == null) {
            // we're only validating the URL & credentials.
            httpRequest = new HeadMethod();
        } else {
            httpRequest = new GetMethod();
        }
        httpRequest.setURI(uri);
        if (usernameOrNull != null) {
            setAuthentication(usernameOrNull, passwordOrNull, client, httpRequest);
        }
        httpRequest.setFollowRedirects(true);
        final Date dateOfLocalContents = timestampOfLocalContents == null ? null : new Date(timestampOfLocalContents);
        final String ifModifiedSince = "If-Modified-Since";
        final String lastModified = "Last-Modified";
        if (dateOfLocalContents != null) {
            final String timestampAsString = DateUtil.formatDate(dateOfLocalContents);
            final Header header = new Header(ifModifiedSince, timestampAsString);
            httpRequest.addRequestHeader(header);
        }
        try {
            final int status = client.executeMethod(httpRequest);
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
                    final Header lastModifiedResponseHeader = httpRequest.getResponseHeader(lastModified);
                    if (lastModifiedResponseHeader == null) {
                        throw new HttpGetException(uri.toString(), usernameOrNull,
                                "due to missing " + lastModified + " header value.");
                    }
                    final String lastModifiedStringValue = lastModifiedResponseHeader.getValue();
                    try {
                        final Date dateFromRemoteServer = DateUtil.parseDate(lastModifiedStringValue);
                        if (dateOfLocalContents == null || dateFromRemoteServer.after(dateOfLocalContents)) {
                            dateOfRemoteContents = dateFromRemoteServer;
                        } else {
                            dateOfRemoteContents = null;
                        }
                    } catch (DateParseException ex) {
                        throw new HttpGetException(uri.toString(), usernameOrNull, "due to invalid " + lastModified
                                + " header value, \"" + lastModifiedStringValue + "\".", ex);
                    }
                    break;
                default :
                    throw new HttpGetException(uri.toString(), usernameOrNull, status);
            }
            if (whereToDownloadToOrNull != null) {
                if (dateOfRemoteContents == null) {
                    // we don't want to do the download after all.
                    skipDownload(whereToDownloadToOrNull, logOrNull, uri, nodeName);
                    httpRequest.abort();
                    return null;
                } else {
                    doDownload(httpRequest, whereToDownloadToOrNull, logOrNull, uri, usernameOrNull, nodeName);
                }
            }
            return dateOfRemoteContents;
        } finally {
            httpRequest.releaseConnection();
        }
    }

    private static void setAuthentication(@CheckForNull final String usernameOrNull,
            @CheckForNull final String passwordOrNull, @Nonnull final HttpClient client,
            @Nonnull final HttpMethodBase httpRequest) throws URIException {
        final UsernamePasswordCredentials httpClientCredentials = new UsernamePasswordCredentials(usernameOrNull,
                passwordOrNull);
        final String host = httpRequest.getURI().getHost();
        final AuthScope scope = new AuthScope(host, -1);
        client.getState().setCredentials(scope, httpClientCredentials);
        httpRequest.setDoAuthentication(true);
        client.getParams().setAuthenticationPreemptive(true);
    }

    private static void skipDownload(@Nonnull final FilePath whereToDownloadTo,
            @CheckForNull final TaskListener logOrNull, @Nonnull final URI uri, @Nonnull final String nodeName) {
        if (logOrNull != null) {
            final String folder = whereToDownloadTo.getRemote();
            final String msg = Messages.AuthenticatedZipExtractionInstaller_download_skipped(uri, folder, nodeName);
            logOrNull.getLogger().println(msg);
        }
    }

    private static void doDownload(@Nonnull final HttpMethodBase httpGet, @Nonnull final FilePath whereToDownloadTo,
            @CheckForNull final TaskListener logOrNull, @Nonnull final URI uri,
            @CheckForNull final String usernameOrNull, @Nonnull final String nodeName)
            throws IOException, InterruptedException, URIException {
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
        final long expectedContentLength = httpGet.getResponseContentLength();
        try (final CountingInputStream cis = new CountingInputStream(httpGet.getResponseBodyAsStream())) {
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
        @Nonnull
        private final String uri;
        @CheckForNull
        private final String usernameOrNull;
        @CheckForNull
        private final Integer httpStatusCodeOrNull;

        private HttpGetException(@Nonnull final String uri, @CheckForNull final String usernameOrNull,
                @CheckForNull Integer httpStatusCodeOrNull, @Nonnull final String reason, @Nullable Throwable cause) {
            super((usernameOrNull == null ? "Anonymous" : "Authenticated") + " HTTP GET of " + uri
                    + (usernameOrNull == null ? "" : (" as " + usernameOrNull)) + " failed, " + reason, cause);
            this.uri = uri;
            this.usernameOrNull = usernameOrNull;
            this.httpStatusCodeOrNull = httpStatusCodeOrNull;
        }

        HttpGetException(@Nonnull final String uri, @CheckForNull final String usernameOrNull, int httpStatusCode) {
            this(uri, usernameOrNull, httpStatusCode,
                    httpStatusCode + " (" + HttpStatus.getStatusText(httpStatusCode) + ")", null);
        }

        HttpGetException(@Nonnull final String uri, @CheckForNull final String usernameOrNull, @Nonnull String reason,
                @Nullable Throwable cause) {
            this(uri, usernameOrNull, null, reason, cause);
        }

        HttpGetException(@Nonnull final String uri, @CheckForNull final String usernameOrNull, @Nonnull String reason) {
            this(uri, usernameOrNull, null, reason, null);
        }

        @Nonnull
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