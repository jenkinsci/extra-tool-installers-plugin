package io.jenkins.plugins.extratoolinstallers.installers;

import java.io.IOException;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Indicates that the installer check did not accept what was found.
 */
class WrongVersionException extends IOException {
    private static final long serialVersionUID = 1L;
    @NonNull
    private final String executableName;
    @NonNull
    private final String whereFound;
    @NonNull
    private final String detectedVersion;
    @CheckForNull
    private final String minVersion;
    @CheckForNull
    private final String maxVersion;

    @Restricted(NoExternalUse.class)
    WrongVersionException(@NonNull final String executableName, @NonNull final String whereFound,
            @NonNull final String detectedVersion, @Nullable final String minVersion, @Nullable final String maxVersion,
            @Nullable Throwable cause) {
        super("Executable '" + executableName + "' at " + whereFound + " is version \"" + detectedVersion
                + "\" but we require" + (minVersion != null ? " >= \"" + minVersion + "\"" : "")
                + (minVersion != null && maxVersion != null ? " and " : "")
                + (maxVersion != null ? " <= \"" + maxVersion + "\"" : ""), cause);
        this.executableName = executableName;
        this.whereFound = whereFound;
        this.detectedVersion = detectedVersion;
        this.minVersion = minVersion;
        this.maxVersion = maxVersion;
    }

    @Restricted(NoExternalUse.class)
    WrongVersionException(@NonNull final String executableName, @NonNull final String whereFound,
            @NonNull final String detectedVersion, @Nullable final String minVersion,
            @Nullable final String maxVersion) {
        this(executableName, whereFound, detectedVersion, minVersion, maxVersion, null);
    }

    public String getExecutableName() {
        return executableName;
    }

    public String getWhereFound() {
        return whereFound;
    }

    public String getDetectedVersion() {
        return detectedVersion;
    }

    public String getMinVersion() {
        return minVersion;
    }

    public String getMaxVersion() {
        return maxVersion;
    }
}
