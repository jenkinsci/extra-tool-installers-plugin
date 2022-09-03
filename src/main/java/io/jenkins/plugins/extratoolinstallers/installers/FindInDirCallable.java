package io.jenkins.plugins.extratoolinstallers.installers;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;

/**
 * Utility class that can find an executable in a given directory.
 */
class FindInDirCallable extends MasterToSlaveFileCallable<String> {
    private static final long serialVersionUID = 1L;
    @Nonnull
    private final String executablePath;
    @CheckForNull
    private final TaskListener logOrNull;

    /**
     * Passed to {@link FilePath#act(FilePath.FileCallable)} in order to run
     * {@link #findInDir(String, TaskListener)} on a remote node.
     *
     * @param executablePath What to look for.
     * @param logOrNull      Where to log build progress. Can be null to suppress
     *                       the normal running commentary.
     */
    @Restricted(NoExternalUse.class)
    FindInDirCallable(@Nonnull String executablePath, @CheckForNull TaskListener logOrNull) {
        this.executablePath = executablePath;
        this.logOrNull = logOrNull;
    }

    public String invoke(@Nonnull File d, VirtualChannel channel) throws IOException, InterruptedException {
        return findInDir(executablePath, logOrNull);
    }

    /**
     * Finds if a given path is an executable file.
     *
     * @param executablePath What to look for.
     * @param logOrNull      Where to log build progress. Can be null to suppress
     *                       the normal running commentary.
     * @return Absolute path to the executable
     * @throws ExecutableNotFoundException if the executable isn't found.
     * @throws IOException                  if we failed for other reasons.
     */
    @Nonnull
    private static String findInDir(@Nonnull final String executablePath,
                                    @CheckForNull final TaskListener logOrNull) throws IOException {
        File possibleExecutable = new File(executablePath);
        if (possibleExecutable.isFile() && possibleExecutable.canExecute()) {
            return possibleExecutable.getAbsolutePath();
        }
        throw new ExecutableNotFoundException(executablePath);
    }

    /**
     * Indicates that we were able to look but didn't find what we were looking for.
     */
    @Restricted(NoExternalUse.class)
    static class ExecutableNotFoundException extends IOException {
        private static final long serialVersionUID = 1L;
        @Nonnull
        private final String executablePath;

        private ExecutableNotFoundException(@Nonnull final String executablePath,
                                            @Nullable Throwable cause) {
            super("Executable '" + executablePath + "' not found.", cause);
            this.executablePath = executablePath;
        }

        ExecutableNotFoundException(@Nonnull final String executablePath) {
            this(executablePath, null);
        }

        public String getExecutablePath() {
            return executablePath;
        }

    }
}
