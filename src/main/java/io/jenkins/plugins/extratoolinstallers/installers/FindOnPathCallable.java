package io.jenkins.plugins.extratoolinstallers.installers;

import java.io.File;
import java.io.IOException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import hudson.FilePath;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

/**
 * Utility class that can find an executable on the path.
 */
class FindOnPathCallable extends MasterToSlaveFileCallable<String> {
    private static final long serialVersionUID = 1L;
    @Nonnull
    private final String executableName;
    @CheckForNull
    private final TaskListener logOrNull;

    /**
     * Passed to {@link FilePath#act(hudson.FilePath.FileCallable)} in order to run
     * {@link #findOnPath(String, TaskListener)} on a remote node.
     * 
     * @param executableName What to look for.
     * @param logOrNull      Where to log build progress. Can be null to suppress
     *                       the normal running commentary.
     */
    @Restricted(NoExternalUse.class)
    FindOnPathCallable(@Nonnull String executableName, @CheckForNull TaskListener logOrNull) {
        this.executableName = executableName;
        this.logOrNull = logOrNull;
    }

    public String invoke(@Nonnull File d, VirtualChannel channel) throws IOException, InterruptedException {
        return findOnPath(executableName, getPath(), logOrNull);
    }

    // package access for test purposes only
    @Restricted(NoExternalUse.class)
    @CheckForNull
    String getPath() {
        return System.getenv("PATH");
    }

    /**
     * Finds an executable by searching the PATH for it.
     * 
     * @param executableName What to look for.
     * @param pathToSearch   The system PATH to scan.
     * @param logOrNull      Where to log build progress. Can be null to suppress
     *                       the normal running commentary.
     * @return Absolute path to the executable
     * @throws ExecutableNotOnPathException if the executable isn't on the path.
     * @throws IOException                  if we failed for other reasons.
     * @throws InterruptedException         if we were interrupted.
     */
    @Nonnull
    private static String findOnPath(@Nonnull final String executableName, @CheckForNull final String pathToSearch,
            @CheckForNull final TaskListener logOrNull) throws IOException, InterruptedException {
        final String path = Util.fixNull(pathToSearch);
        final String[] pathElements = path.split(File.pathSeparator);
        for (final String dirOnPath : pathElements) {
            final File possibleExecutable = new File(dirOnPath, executableName);
            if (possibleExecutable.isFile() && possibleExecutable.canExecute()) {
                return possibleExecutable.getAbsolutePath();
            }
        }
        throw new ExecutableNotOnPathException(executableName, path);
    }

    /**
     * Indicates that we were able to look but didn't find what we were looking for.
     */
    @Restricted(NoExternalUse.class)
    static class ExecutableNotOnPathException extends IOException {
        private static final long serialVersionUID = 1L;
        @Nonnull
        private final String executableName;
        @Nonnull
        private final String path;

        private ExecutableNotOnPathException(@Nonnull final String executableName, @Nonnull final String path,
                @Nullable Throwable cause) {
            super("Executable '" + executableName + "' not found on PATH, " + path, cause);
            this.executableName = executableName;
            this.path = path;
        }

        ExecutableNotOnPathException(@Nonnull final String executableName, @Nonnull final String path) {
            this(executableName, path, null);
        }

        public String getExecutableName() {
            return executableName;
        }

        public String getPath() {
            return path;
        }
    }
}
