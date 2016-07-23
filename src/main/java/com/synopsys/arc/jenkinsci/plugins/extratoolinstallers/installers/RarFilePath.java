/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Eric Lefevre-Ardant, Erik Ramfelt, Michael B. Donohue, Alan Harder,
<<<<<<< HEAD
 * Manufacture Francaise des Pneumatiques Michelin, Romain Seguy,
 * Martin Hjelmqvist
 *
=======
 * Manufacture Francaise des Pneumatiques Michelin, Romain Seguy
 * 
>>>>>>> e8520f80c9453fea5d070581b19a4b3650da04a8
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.synopsys.arc.jenkinsci.plugins.extratoolinstallers.installers;

/**
<<<<<<< HEAD
 * A version of FilePath modified to extract RAR files instead of ZIP and TAR.GZ. 
 * Intended to fit the needs of the RarExtractionInstaller. 
 * Inspired by FilePath in the Hudson core.
 *
=======
 * 
 * Inspired by FilePath in the Hudson core.
>>>>>>> e8520f80c9453fea5d070581b19a4b3650da04a8
 * @author Martin Hjelmqvist <martin@hjelmqvist.eu>.
 */
import com.github.junrar.testutil.ExtractArchive;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.ProxyConfiguration;
import hudson.model.TaskListener;
import hudson.remoting.RemoteInputStream;
import hudson.remoting.VirtualChannel;
import hudson.util.IOUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
<<<<<<< HEAD
=======
import java.util.Date;
>>>>>>> e8520f80c9453fea5d070581b19a4b3650da04a8
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.MasterToSlaveFileCallable;
import jenkins.SlaveToMasterFileCallable;
import org.apache.commons.io.input.CountingInputStream;

public class RarFilePath implements Serializable {

    private FilePath dir;

    public RarFilePath(FilePath dir) {
        this.dir = dir;
    }

    public <T> T act(final FileCallable<T> callable) throws IOException, InterruptedException {
        return dir.act(callable);
    }

<<<<<<< HEAD
    /**
     * Given a rar file, extracts it to the given target directory, if necessary.
     *
     * <p>
     * This method is a convenience method designed for installing a binary package to a location that supports upgrade and downgrade. Specifically,
     *
     * <ul>
     * <li>If the target directory doesn't exist {@linkplain #mkdirs() it will be created}.
     * <li>The timestamp of the archive is left in the installation directory upon extraction.
     * <li>If the timestamp left in the directory does not match the timestamp of the current archive file, the directory contents will be discarded and the archive file will be re-extracted.
     * <li>If the connection is refused but the target directory already exists, it is left alone.
     * </ul>
     *
     * @param archive The resource that represents the rar file. This URL must support the {@code Last-Modified} header. (For example, you could use {@link ClassLoader#getResource}.)
     * @param listener If non-null, a message will be printed to this listener once this method decides to extract an archive, or if there is any issue.
     * @param message a message to be printed in case extraction will proceed.
     * @return true if the archive was extracted, false if the extraction was skipped because the target directory was considered up to date or an error occurred.
     */
=======
>>>>>>> e8520f80c9453fea5d070581b19a4b3650da04a8
    public boolean installIfNecessaryFrom(@Nonnull URL archive, @CheckForNull TaskListener listener, @Nonnull String message) throws IOException, InterruptedException {
        try {
            FilePath timestamp = dir.child(".timestamp");
            long lastModified = timestamp.lastModified();
            URLConnection con;
            try {
                con = ProxyConfiguration.open(archive);
                if (lastModified != 0) {
                    con.setIfModifiedSince(lastModified);
                }
                con.connect();
            } catch (IOException x) {
                if (dir.exists()) {
                    // Cannot connect now, so assume whatever was last unpacked is still OK.
                    if (listener != null) {
                        listener.getLogger().println("Skipping installation of " + archive + " to " + dir.getRemote() + ": " + x);
                    }
                    return false;
                } else {
                    throw x;
                }
            }

            if (lastModified != 0 && con instanceof HttpURLConnection) {
                HttpURLConnection httpCon = (HttpURLConnection) con;
                int responseCode = httpCon.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    return false;
                } else if (responseCode != HttpURLConnection.HTTP_OK) {
                    listener.getLogger().println("Skipping installation of " + archive + " to " + dir.getRemote() + " due to server error: " + responseCode + " " + httpCon.getResponseMessage());
                    return false;
                }
            }

            long sourceTimestamp = con.getLastModified();

            if (dir.exists()) {
                if (lastModified != 0 && sourceTimestamp == lastModified) {
                    return false;   // Tool is up to date.
                }
                dir.deleteContents();
            } else {
                dir.mkdirs();
            }

            if (listener != null) {
                listener.getLogger().println(message);
            }

            if (dir.isRemote()) {
                // First try to download from the slave machine.
                try {
                    act(new Unpack(archive));
                    timestamp.touch(sourceTimestamp);
                    return true;
                } catch (IOException x) {
                    if (listener != null) {
                        x.printStackTrace(listener.error("Failed to download " + archive + " from slave; will retry from master"));
                    }
                }
            }

            // for HTTP downloads, enable automatic retry for added resilience
            InputStream in = archive.getProtocol().startsWith("http") ? ProxyConfiguration.getInputStream(archive) : con.getInputStream();
            CountingInputStream cis = new CountingInputStream(in);
            try {
<<<<<<< HEAD
                if (archive.toExternalForm().endsWith(".rar")) {
                    unrarFrom(cis);
                }
=======
                unrarFrom(cis);
>>>>>>> e8520f80c9453fea5d070581b19a4b3650da04a8
            } catch (IOException e) {
                throw new IOException(String.format("Failed to unpack %s (%d bytes read of total %d)",
                        archive, cis.getByteCount(), con.getContentLength()), e);
            }
            timestamp.touch(sourceTimestamp);
            return true;
        } catch (IOException e) {
            throw new IOException("Failed to install " + archive + " to " + dir.getRemote(), e);
        }
    }

    // this reads from arbitrary URL
    private final class Unpack extends MasterToSlaveFileCallable<Void> {

        private final URL archive;

        Unpack(URL archive) {
            this.archive = archive;
        }

        @Override
        public Void invoke(File dir, VirtualChannel channel) throws IOException, InterruptedException {
            InputStream in = archive.openStream();
            try {
                CountingInputStream cis = new CountingInputStream(in);
                try {
                    unrar(dir, cis);
                } catch (IOException x) {
                    throw new IOException(String.format("Failed to unpack %s (%d bytes read)", archive, cis.getByteCount()), x);
                }
            } finally {
                in.close();
            }
            return null;
        }
    }

    public void unrarFrom(InputStream in) throws IOException {
        final InputStream inRar;
        inRar = new RemoteInputStream(in);
        try {
            act(new SlaveToMasterFileCallable<Void>() {
                public Void invoke(File dir, VirtualChannel channel) throws IOException {
                    unrar(dir, inRar);
                    return null;
                }
                private static final long serialVersionUID = 1L;
            });
        } catch (InterruptedException e) {
            Thread.interrupted();
        }
    }

<<<<<<< HEAD
    /**
     * Converts the InputStream to a file, then performs the extraction into the file 'target' using the JUnrar library.
     * 
     * @param target Where the tool will be installed.
     * @param inRar RAR file to be extracted.
     * @throws IOException 
     */
    private void unrar(File target, InputStream inRar)
            throws IOException {
        File archive = File.createTempFile("tempRar", null);

        try {
            IOUtils.copy(inRar, archive);
            unrar(target, archive);
=======
    private void unrar(File dir, InputStream inRar)
            throws IOException {
        File archive = File.createTempFile("tmprar", null);

        try {
            IOUtils.copy(inRar, archive);
            unrar(dir, archive);
>>>>>>> e8520f80c9453fea5d070581b19a4b3650da04a8
        } finally {
            archive.delete();
        }
    }

    /**
<<<<<<< HEAD
     * Performs the extraction specified archive file into the file 'target' using the JUnrar library.
=======
     * Performs the extraction of 'archive' into the file 'target' using the
     * JUnrar library.
>>>>>>> e8520f80c9453fea5d070581b19a4b3650da04a8
     *
     * @param target Where the tool will be installed.
     * @param archive RAR file to be extracted.
     */
    private void unrar(File target, File archive) {
<<<<<<< HEAD
        ExtractArchive.extractArchive(archive, target);
    }
=======
        ExtractArchive extractor = new ExtractArchive();
        extractor.extractArchive(archive, target);
        Date date = new Date();
//        Check whether this is necessary.
        target.setLastModified(date.getTime());
    }

>>>>>>> e8520f80c9453fea5d070581b19a4b3650da04a8
}
