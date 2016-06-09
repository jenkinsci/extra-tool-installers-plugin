package com.synopsys.arc.jenkinsci.plugins.extratoolinstallers.installers;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import hudson.AbortException;
import hudson.remoting.*;
import hudson.ExtensionPoint;
import hudson.ExtensionList;
import hudson.Extension;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.model.TaskListener;
import hudson.model.Node;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstallerDescriptor;
import hudson.util.FormValidation;
import hudson.util.DaemonThreadFactory;
import hudson.util.IOUtils;
import hudson.util.NamingThreadFactory;
import hudson.util.ExceptionCatchingThreadFactory;

//import jenkins.MasterToSlaveFileCallable;
import jenkins.SlaveToMasterFileCallable;
import jenkins.util.ContextResettingExecutorService;
import jenkins.FilePathFilter;
import jenkins.SoloFilePathFilter;

import org.jenkinsci.remoting.*;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

import org.apache.commons.io.input.CountingInputStream;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class RarExtractionInstaller extends AbstractExtraToolInstaller {

	/**
	 * URL of a RAR file which should be downloaded in case the tool is missing.
	 */
	private final String toolHome;

	private VirtualChannel channel;

	private String remote;

	/*
	 * Max amount of redirects allowed. Predefined? Necessary?
	 */
	private static final int MAX_REDIRECTS = 20;

	private transient @Nullable SoloFilePathFilter filter;
	
	/*
	 * String Messages for the DescriptorImpl class. This should be moved to the
	 * class "Messages" later.
	 */
	public static final String RAR_EXTRACTION_INSTALLER_BAD_CONNECTION = "Server rejected connection.";
	public static final String RAR_EXTRACTION_INSTALLER_COULD_NOT_CONNECT = "Could not connect to URL.";
	public static final String RAR_EXTRACTION_INSTALLER_DISPLAY_NAME = "--- Extract *.rar ---------";
	public static final String RAR_EXTRACTION_INSTALLER_MALFORMED_URL = "Malformed URL.";

	@DataBoundConstructor
	public RarExtractionInstaller(String label, String toolHome,
			boolean failOnSubstitution) {
		super(label, toolHome, failOnSubstitution);
		this.toolHome = toolHome;
	}

	public String getUrl() {
		return toolHome;
	}

	@Override
	public FilePath performInstallation(ToolInstallation tool, Node node,
			TaskListener log) throws IOException, InterruptedException {

		FilePath dir = preferredLocation(tool, node);
		this.remote = dir.getRemote();
		this.channel = dir.getChannel();
		
		if (installIfNecessaryFrom(
				dir,
				new URL(super.getToolHome()),
				log,
				"Unpacking " + toolHome + " to " + dir + " on "
						+ node.getDisplayName(), MAX_REDIRECTS)) {
			log.getLogger().println("RAR extraction successful.");
		}
		// Is a subdir necessary?
		return dir;
	}

	// Source: hudson.tools.FilePath.java
	private boolean installIfNecessaryFrom(FilePath dir, @Nonnull URL archive,
			@CheckForNull TaskListener listener, @Nonnull String message,
			int maxRedir) throws IOException, InterruptedException {

		listener.getLogger().println(
				"----- TROUBLESHOOTING - InstallIfNecessary has been entered.");

		try {
			FilePath timestamp = dir.child(".timestamp");
			long lastModified = timestamp.lastModified();
			URLConnection con = null;
			try {
				con = ProxyConfiguration.open(archive);
				if (lastModified != 0) {
					con.setIfModifiedSince(lastModified);
				}
				con.connect();
				listener.getLogger().println(
						"----- TROUBLESHOOTING - con.connect.");
			} catch (IOException x) {
				if (dir.exists()) {
					if (listener != null) {
						listener.getLogger().println(
								"Skipping installation of " + archive + " to "
										+ archive + ": " + x);
						return false;
					}
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
					listener.getLogger().println(
							"Skipping installation of " + archive + " to "
									+ archive + " due to server error: "
									+ responseCode + " "
									+ httpCon.getResponseMessage());
					return false;
				}
			}

			long sourceTimestamp = con.getLastModified();

			if (dir.exists()) {
				if (lastModified != 0 && sourceTimestamp == lastModified) {
					listener.getLogger()
							.println(
									"----- TROUBLESHOOTING - lastModified. Already up to date.");
					return false;
				}
				dir.deleteContents();
			} else {
				listener.getLogger().println(
						"----- TROUBLESHOOTING - dir.mkdirs().");
				dir.mkdirs();
			}
			if (listener != null) {
				listener.getLogger().println(message);
			}

			if (dir.isRemote()) {
				// First try to download from the slave machine.
				try {
					/* Get the Unpack method from FilePath also? */
//					dir.act(new Unpack(archive));
					timestamp.touch(sourceTimestamp);
					return true;
				} catch (IOException x) {
					if (listener != null) {
						x.printStackTrace(listener.error("Failed to download "
								+ archive
								+ " from slave; will retry from master"));
					}
				}
			}

			// for HTTP downloads, enable automatic retry for added resilience
			InputStream in;
			if (archive.getProtocol().startsWith("http")) {
				in = ProxyConfiguration.getInputStream(archive);
				listener.getLogger().println(
						"----- TROUBLESHOOTING - Recognized http protocol.");
				in = con.getInputStream();
			} else {
				in = con.getInputStream();
			}
			CountingInputStream cis = new CountingInputStream(in);
			unrarFrom(cis, listener);
			// try {
			// // Or extract here?
			// } catch (IOException e) {
			// throw new IOException(String.format(
			// "Failed to unpack %s (%d bytes read of total %d)",
			// archive, cis.getByteCount(), con.getContentLength()), e);
			// }
			timestamp.touch(sourceTimestamp);
			in.close(); // Maybe not needed
			return true;

		} catch (IOException e) {
			throw new IOException("Failed to install " + archive + " to "
					+ archive, e);
		}

	}

	// Source: hudson.tools.FilePath.java (Changed name)
	public void unrarFrom(InputStream _in, final TaskListener listener) {
		listener.getLogger().println(
				"----- TROUBLESHOOTING - 1/3 unrarFrom entered.");
		final InputStream inRar = new CountingInputStream(_in);
		try{
		act(new SecureFileCallable<Void>() {
			public Void invoke(File dir, VirtualChannel channel) throws IOException {
				unrar(dir, inRar, listener);
				return null;
			}
			private static final long serialVersionUID = 1L;
		});
		}catch(IOException e){
			// Handle exception
		}catch(InterruptedException e){
			// Handle exception
		}
		
	}

	// Source: hudson.tools.FilePath.java (Changed name)
	private void unrar(File dir, InputStream inRar, TaskListener listener)
			throws IOException {
		listener.getLogger().println(
				"----- TROUBLESHOOTING - 2/3 unrar entered.");
		File tmpRar = File.createTempFile("tmprar", null);
		
		try {
			IOUtils.copy(inRar, tmpRar);
			unrar(dir, tmpRar, listener);
		} finally {
			tmpRar.delete();
		}
	}

	
	/**
	 * Performs the extraction of tmpRar into the file target using the JUnrar library. 
	 * 
	 * @param target Where the tool will be installed.
	 * @param tmpRar Installation file.
	 * @param listener TaskListener for troubleshooting. Can be removed in final version.
	 */
	private void unrar(File target, File tmpRar, TaskListener listener) {
		listener.getLogger().println(
				"----- TROUBLESHOOTING - 3/3 Final unrar entered.");
		Archive arch = null;
		
		// Code below makes the installer not visible in Jenkins.
		// Problem with the JUnrar lib probably.
//		try {
//			arch = new Archive(tmpRar);
//			if (arch != null) {
//				if (arch.isEncrypted()) {
//					listener.getLogger()
//							.println(
//									"----- TROUBLESHOOTING - Archive encrypted. Extraction aborted.");
//					return;
//				}
//				FileHeader fh = arch.nextFileHeader();
//				while (fh != null) {
//					
//					if (fh.isEncrypted()) {
//						listener.getLogger().println(
//								"----- TROUBLESHOOTING - Encrypted file in the archive. File: "
//										+ fh.getFileNameString());
//						continue;
//					}
//
//					listener.getLogger().println(
//							"----- TROUBLESHOOTING - Extracting: " + fh.getFileNameString());
//					File f = new File(target, fh.getFileNameString());
//					if (fh.isDirectory()) {
//						/* Next in line is a folder -> create a directory */
//						mkdirs(f);
//					} else {
//						/* Next in line is a file -> create a file */
//						File p = f.getParentFile();
//						if(p != null){
//							mkdirs(p);
//						}
//						
//						InputStream input = arch.getInputStream(fh);
//						try {
//	                        IOUtils.copy(input, writing(f));
//	                    } finally {
//	                        input.close();
//	                    }
//						// File f = createFile(fh, destination);
//						// OutputStream stream = new FileOutputStream(f);
//						// arch.extractFile(fh, stream);
//						// stream.close();
//					}
//					f.setLastModified(fh.getCTime().getTime());		// Possibly wrong call from 'fh'. (getCtime/getMTime/getATime)
//					fh = arch.nextFileHeader();
//				}
//			}
//		} catch (IOException e) {
//			/* --- Handle exception --- */
//		} catch (RarException e) {
//			/* --- Handle exception --- */
//		}
	}

	// Source: hudson.tools.FilePath.java
	private boolean mkdirs(File dir) {
        if (dir.exists())   return false;

        filterNonNull().mkdirs(dir);
        return dir.mkdirs();
    }
	
	// Source: hudson.tools.FilePath.java
	/**
     * Pass through 'f' after ensuring that we can write to that file.
     */
    private File writing(File f) {
        FilePathFilter filter = filterNonNull();
        if (!f.exists())
            filter.create(f);
        filter.write(f);
        return f;
    }
	
	// Source: hudson.tools.FilePath.java
	private @Nonnull SoloFilePathFilter filterNonNull() {
		return filter != null ? filter : UNRESTRICTED;
	}

	// Source: hudson.tools.FilePath.java
	private static final SoloFilePathFilter UNRESTRICTED = SoloFilePathFilter.wrap(FilePathFilter.UNRESTRICTED);
	 
	// Source: hudson.tools.FilePath.java
	/* Might need to normalize the path, keep this in mind */
	private static String normalize(String path) {
		return "";
	}

	// Source: hudson.tools.FilePath.java
	/**
	 * {@link FileCallable}s that can be executed anywhere, including the
	 * master.
	 * 
	 * The code is the same as {@link SlaveToMasterFileCallable}, but used as a
	 * marker to designate those impls that use {@link FilePathFilter}.
	 */
	static abstract class SecureFileCallable<T> extends
			SlaveToMasterFileCallable<T> {
		private static final long serialVersionUID = 1L;
	}

	// Source: hudson.tools.FilePath.java
	/**
	 * Executes some program on the machine that this {@link FilePath} exists,
	 * so that one can perform local file operations.
	 */
	public <T> T act(final SecureFileCallable<T> callable) throws IOException,
			InterruptedException {
		return act(callable, callable.getClass().getClassLoader());
	}

	// Source: hudson.tools.FilePath.java
	private <T> T act(final SecureFileCallable<T> callable, ClassLoader cl)
			throws IOException, InterruptedException {
		if (channel != null) {
			// run this on a remote system
			try {
				DelegatingCallable<T, IOException> wrapper = new FileCallableWrapper<T>(
						callable, cl);
				for (FileCallableWrapperFactory factory : ExtensionList
						.lookup(FileCallableWrapperFactory.class)) {
					wrapper = factory.wrap(wrapper);
				}
				return channel.call(wrapper);
			} catch (TunneledInterruptedException e) {
				throw (InterruptedException) new InterruptedException(
						e.getMessage()).initCause(e);
			} catch (AbortException e) {
				throw e; // pass through so that the caller can catch it as
							// AbortException
			} catch (IOException e) {
				// wrap it into a new IOException so that we get the caller's
				// stack trace as well.
				throw new IOException("remote file operation failed: " + remote
						+ " at " + channel + ": " + e, e);
			}
		} else {
			// the file is on the local machine.
			return callable.invoke(new File(remote), localChannel);
		}
	}

	// Source: hudson.tools.FilePath.java
	/**
	 * Code that gets executed on the machine where the {@link FilePath} is
	 * local. Used to act on {@link FilePath}. <strong>Warning:</code>
	 * implementations must be serializable, so prefer a static nested class to
	 * an inner class.
	 * 
	 * <p>
	 * Subtypes would likely want to extend from either
	 * {@link MasterToSlaveCallable} or {@link SlaveToMasterFileCallable}.
	 * 
	 * @see FilePath#act(FileCallable)
	 */
	public interface FileCallable<T> extends Serializable, RoleSensitive {
		/**
		 * Performs the computational task on the node where the data is
		 * located.
		 * 
		 * <p>
		 * All the exceptions are forwarded to the caller.
		 * 
		 * @param f
		 *            {@link File} that represents the local file that
		 *            {@link FilePath} has represented.
		 * @param channel
		 *            The "back pointer" of the {@link Channel} that represents
		 *            the communication with the node from where the code was
		 *            sent.
		 */
		T invoke(File f, VirtualChannel channel) throws IOException,
				InterruptedException;
	}

	// Source: hudson.tools.FilePath.java
	/**
	 * This extension point allows to contribute a wrapper around a fileCallable
	 * so that a plugin can "intercept" a call.
	 * <p>
	 * The {@link #wrap(hudson.remoting.DelegatingCallable)} method itself will
	 * be executed on master (and may collect contextual data if needed) and the
	 * returned wrapper will be executed on remote.
	 * 
	 * @since 1.482
	 * @see AbstractInterceptorCallableWrapper
	 */
	public static abstract class FileCallableWrapperFactory implements
			ExtensionPoint {

		public abstract <T> DelegatingCallable<T, IOException> wrap(
				DelegatingCallable<T, IOException> callable);

	}

	// Source: hudson.tools.FilePath.java
	/**
	 * Adapts {@link FileCallable} to {@link Callable}.
	 */
	private class FileCallableWrapper<T> implements
			DelegatingCallable<T, IOException> {
		private final SecureFileCallable<T> callable;
		private transient ClassLoader classLoader;

//		public FileCallableWrapper(FileCallable<T> callable) {
//			this.callable = callable;
//			this.classLoader = callable.getClass().getClassLoader();
//		}

		private FileCallableWrapper(SecureFileCallable<T> callable2,
				ClassLoader classLoader) {
			this.callable = callable2;
			this.classLoader = classLoader;
		}

		public T call() throws IOException {
			try {
				return callable.invoke(new File(remote), Channel.current());
			} catch (InterruptedException e) {
				throw new TunneledInterruptedException(e);
			}
		}

		/**
		 * Role check comes from {@link FileCallable}s.
		 */
		@Override
		public void checkRoles(RoleChecker checker) throws SecurityException {
			callable.checkRoles(checker);
		}

		public ClassLoader getClassLoader() {
			return classLoader;
		}

		private static final long serialVersionUID = 1L;
	}

	// Source: hudson.tools.FilePath.java
	/**
	 * Used to tunnel {@link InterruptedException} over a Java signature that
	 * only allows {@link IOException}
	 */
	private static class TunneledInterruptedException extends IOException {
		private TunneledInterruptedException(InterruptedException cause) {
			super(cause);
		}

		private static final long serialVersionUID = 1L;
	}

	// Source: hudson.tools.FilePath.java
	/* --- Added a cast to ExecutorService here. Gave error otherwise. ---*/
	private static final ExecutorService threadPoolForRemoting = (ExecutorService) new ContextResettingExecutorService (
			Executors.newCachedThreadPool(new ExceptionCatchingThreadFactory(
					new NamingThreadFactory(new DaemonThreadFactory(),
							"FilePath.localPool"))));

	// Source: hudson.tools.FilePath.java
	public static LocalChannel localChannel = new LocalChannel(
			threadPoolForRemoting);


	@Extension
	public static class DescriptorImpl extends
			ToolInstallerDescriptor<RarExtractionInstaller> {

		public String getDisplayName() {
			return RAR_EXTRACTION_INSTALLER_DISPLAY_NAME;
		}

		public FormValidation doCheckUrl(@QueryParameter String value) {
			try {
				URLConnection conn = ProxyConfiguration.open(new URL(value));
				conn.connect();
				if (conn instanceof HttpURLConnection) {
					if (((HttpURLConnection) conn).getResponseCode() != HttpURLConnection.HTTP_OK) {
						return FormValidation
								.error(RAR_EXTRACTION_INSTALLER_COULD_NOT_CONNECT);
					}
				}
				return FormValidation.ok();
			} catch (MalformedURLException x) {
				return FormValidation
						.error(RAR_EXTRACTION_INSTALLER_MALFORMED_URL);
			} catch (IOException x) {
				return FormValidation.error(x,
						RAR_EXTRACTION_INSTALLER_COULD_NOT_CONNECT);
			}
		}
	}
}