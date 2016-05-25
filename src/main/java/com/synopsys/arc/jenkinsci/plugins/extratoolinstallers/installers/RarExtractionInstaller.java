package com.synopsys.arc.jenkinsci.plugins.extratoolinstallers.installers;



import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;


import hudson.Extension;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import hudson.model.TaskListener;
import hudson.model.Node;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstallerDescriptor;
import hudson.util.FormValidation;
import hudson.util.IOUtils;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

import org.apache.commons.io.input.CountingInputStream;

import org.kohsuke.stapler.QueryParameter;

public class RarExtractionInstaller extends AbstractExtraToolInstaller {

	/*
	 * URL of a RAR file which should be downloaded in case the tool is missing.
	 */
	private final String toolHome;

	private static final int MAX_REDIRECTS = 20;
	
	public static final String RAR_EXTRACTION_INSTALLER_BAD_CONNECTION = "Server rejected connection.";
	public static final String RAR_EXTRACTION_INSTALLER_COULD_NOT_CONNECT = "Could not connect to URL.";
	public static final String RAR_EXTRACTION_INSTALLER_DISPLAY_NAME = "Extract *.rar";
	public static final String RAR_EXTRACTION_INSTALLER_MALFORMED_URL = "Malformed URL.";

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
		if (installIfNecessaryFrom(
				dir,
				new URL(super.getToolHome()),
				log,
				"Unpacking " + toolHome + " to " + dir + " on "
						+ node.getDisplayName(), MAX_REDIRECTS)) {
			log.getLogger().println("RAR extraction successful.");
		}
		// Inget subdir om det inte behövs.
		return dir;
	}

	private boolean installIfNecessaryFrom(FilePath dir, @Nonnull URL archive,
			@CheckForNull TaskListener listener, @Nonnull String message,
			int maxRedir) throws IOException, InterruptedException {
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
			} catch (IOException x) {
				if (dir.exists()) {
					// Cannot connect now, so assume whatever was last unpacked
					// is still OK.
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
				if (lastModified != 0 && sourceTimestamp == lastModified)
					return false; // already up to date
				// Fungerar deras metoder i mitt fall?
				dir.deleteContents();
			} else {
				// Fungerar deras metoder i mitt fall?
				dir.mkdirs();
			}
			if (listener != null) {
				listener.getLogger().println(message);
			}

			// FELKÄLLA eventuellt.
			// Fungerar deras metoder i mitt fall?
			// if (dir.isRemote()) {
			// // First try to download from the slave machine.
			// try {
			// // Ska detta göras?
			// dir.act(new Unpack(archive));
			// timestamp.touch(sourceTimestamp);
			// return true;
			// } catch (IOException x) {
			// if (listener != null) {
			// x.printStackTrace(listener.error("Failed to download " + archive
			// + " from slave; will retry from master"));
			// }
			// }
			// }

			// for HTTP downloads, enable automatic retry for added resilience
			InputStream in;
			if (archive.getProtocol().startsWith("http")) {
				// in = ProxyConfiguration.getInputStream(archive);
				in = con.getInputStream();
			} else {
				in = con.getInputStream();
			}
			CountingInputStream cis = new CountingInputStream(in);
			unrarFrom(cis);
			// try{
			// // Packa upp här (med CountingIS?)
			//
			// }catch(IOException e){
			// throw new
			// IOException(String.format("Failed to unpack %s (%d bytes read of total %d)",
			// archive,cis.getByteCount(),con.getContentLength()),e);
			// }
			timestamp.touch(sourceTimestamp);
			return true;

		} catch (IOException e) {
			throw new IOException("Failed to install " + archive + " to "
					+ archive, e);
		}
	}

	private void unrarFrom(InputStream _in) {
		final InputStream in = new CountingInputStream(_in);
		try{
			unrar(new File(toolHome), in);
		}catch(IOException e){
//			throw new IOException("Failed installation - IOException in unrarFrom(...)", e);
		}
	}

	private void unrar(File dir, InputStream in) throws IOException {
		File tmpFile = File.createTempFile("tmpzip", null); // uses
															// java.io.tmpdir
		try {
			// TODO why does this not simply use ZipInputStream?
			IOUtils.copy(in, tmpFile);
			unrar(dir, tmpFile);
		} finally {
			tmpFile.delete();
		}
	}

	private void unrar(File archive, File tmpFile) {
		Archive arch = null;
		try {
			arch = new Archive(archive);
			if (arch != null) {
				if (arch.isEncrypted()) {
					System.out.println("Archive encrypted. Aborting.");
					return;
				}
				FileHeader fh = null;
				while (true) {
					fh = arch.nextFileHeader();
					if (fh == null) {
						break;
					}
					if (fh.isEncrypted()) {
						System.out.println("File encrypted: "
								+ fh.getFileNameString());
						continue;
					}

					System.out.println("Extracting: " + fh.getFileNameString());
					if (fh.isDirectory()) {
//						 createDirectory(fh, destination);
//						mkdirs(new File(fh.getFileNameString()));
					} else {
//						 File f = createFile(fh, destination);
//						OutputStream stream = new FileOutputStream(f);
//						arch.extractFile(fh, stream);
//						stream.close();
					}
				}
			}
		} catch (IOException e) {
			// Handle exception
		} catch (RarException e) {
			// Handle exception
		}
	}

//	private boolean mkdirs(File dir) {
//		if (dir.exists())
//			return false;
//
//		filterNonNull().mkdirs(dir);
//		return dir.mkdirs();
//	}

//	private @Nonnull
//	SoloFilePathFilter filterNonNull() {
//		return filter != null ? filter : UNRESTRICTED;
//	}

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
						return FormValidation.error(RAR_EXTRACTION_INSTALLER_COULD_NOT_CONNECT);
					}
				}
				return FormValidation.ok();
			} catch (MalformedURLException x) {
				return FormValidation.error(RAR_EXTRACTION_INSTALLER_MALFORMED_URL);
			} catch (IOException x) {
				return FormValidation.error(x,
						RAR_EXTRACTION_INSTALLER_COULD_NOT_CONNECT);
			}
		}

	}
	
}

	// private static String normalize(String path){
	// // Eventuellt behövs en private metod som "normaliserar"
	// }

	// private boolean mkdirs(File dir) {
	// if (dir.exists()) return false;
	//
	// filterNonNull().mkdirs(dir);
	// return dir.mkdirs();
	// }
	//
	//
	
	
	
	
	
	
	// -----------------------------------------------------------------------------------------------
	// // --------- Sub-methods for installIfNecessary
	// --------------------------------------------------
	// //
	// -----------------------------------------------------------------------------------------------
	//
	// public boolean exists() throws IOException, InterruptedException {
	// return act(new SecureFileCallable<Boolean>() {
	// private static final long serialVersionUID = 1L;
	// public Boolean invoke(File f, VirtualChannel channel) throws IOException
	// {
	// return stating(f).exists();
	// }
	// });
	// }
	//
	// public <T> T act(final FileCallable<T> callable) throws IOException,
	// InterruptedException {
	// return act(callable,callable.getClass().getClassLoader());
	// }
	//
	// private <T> T act(final FileCallable<T> callable, ClassLoader cl) throws
	// IOException, InterruptedException {
	// if(channel!=null) {
	// // run this on a remote system
	// try {
	// DelegatingCallable<T,IOException> wrapper = new
	// FileCallableWrapper<T>(callable, cl);
	// for (FileCallableWrapperFactory factory :
	// ExtensionList.lookup(FileCallableWrapperFactory.class)) {
	// wrapper = factory.wrap(wrapper);
	// }
	// return channel.call(wrapper);
	// } catch (TunneledInterruptedException e) {
	// throw (InterruptedException)new
	// InterruptedException(e.getMessage()).initCause(e);
	// } catch (AbortException e) {
	// throw e; // pass through so that the caller can catch it as
	// AbortException
	// } catch (IOException e) {
	// // wrap it into a new IOException so that we get the caller's stack trace
	// as well.
	// throw new IOException("remote file operation failed: " + remote + " at "
	// + channel + ": " + e, e);
	// }
	// } else {
	// // the file is on the local machine.
	// return callable.invoke(new File(remote), localChannel);
	// }
	// }
	//
	// 