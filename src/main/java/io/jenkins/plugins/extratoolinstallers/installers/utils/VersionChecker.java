package io.jenkins.plugins.extratoolinstallers.installers.utils;

import hudson.Util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionChecker {
    /**
     * Extracts a version string from a command's output.
     *
     * @param versionPattern The pattern we're using to find the version.
     * @param cmdOutput      The command output we are parsing.
     * @return null if no match, else the concatenation of all capturing groups
     * within the pattern.
     */
    public static String parseVersionCmdOutputForVersion(Pattern versionPattern, String cmdOutput) {
        for (final String cmdLine : cmdOutput.split("\\R")) {
            final Matcher matcher = versionPattern.matcher(cmdLine);
            if (matcher.matches()) {
                final int gc = matcher.groupCount();
                final StringBuilder result = new StringBuilder();
                for (int g = 1; g <= gc; g++) {
                    final String group = matcher.group(g);
                    if (group != null) {
                        result.append(group);
                    }
                }
                return result.toString();
            }
        }
        return null;
    }

    /**
     * Compares a version string to the specified min & max.
     *
     * @param versionMin    The minimum acceptable version (inclusive, i.e. this
     *                      version is acceptable). Can be null if there is no
     *                      minimum.
     * @param versionMax    The maximum acceptable version (inclusive, i.e. this
     *                      version is acceptable). Can be null if there is no
     *                      maximum.
     * @param actualVersion The version to be checked.
     * @return 0 if the version is acceptable, -ve if the version is below the
     * minimum, +ve is the version is above the maximum.
     */
    public static int checkVersionIsInRange(@Nullable String versionMin, @Nullable String versionMax, @Nullable String actualVersion) {
        if (Util.fixEmpty(versionMin) != null) {
            final int cmpParsedToMin = compareVersions(actualVersion, versionMin);
            if (cmpParsedToMin < 0) {
                return -1; // parsedVersion is lower than versionMin
            }
        }
        if (Util.fixEmpty(versionMax) != null) {
            final int cmpParsedToMax = compareVersions(actualVersion, versionMax);
            if (cmpParsedToMax > 0) {
                return 1; // parsedVersion is higher than versionMax
            }
        }
        return 0;
    }

    /**
     * @return 0 if versions are equivalent, +ve if compoundVersionA is higher than
     * compoundVersionB, -ve if compoundVersionA is lower.
     */
    public static int compareVersions(@Nullable String compoundVersionA, @Nullable String compoundVersionB) {
        final String[] splitA = compoundVersionA == null ? new String[0] : compoundVersionA.split("\\.", -1);
        final String[] splitB = compoundVersionB == null ? new String[0] : compoundVersionB.split("\\.", -1);
        final int lengthA = splitA.length;
        final int lengthB = splitB.length;
        final int highestLength = Math.max(lengthA, lengthB);
        for (int i = 0; i < highestLength; i++) {
            final String partA = lengthA > i ? splitA[i] : null;
            final String partB = lengthB > i ? splitB[i] : null;
            final int result = compareNullableVersionParts(partA, partB);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    /**
     * @return 0 if versions are equivalent, +ve if partA is higher than partB, -ve
     * if partA is lower.
     */
    private static int compareNullableVersionParts(@Nullable String partA, @Nullable String partB) {
        if (partA == null) {
            if (partB == null) {
                return 0;
            } else {
                return -1;
            }
        } else {
            if (partB == null) {
                return 1;
            } else {
                return compareVersionParts(partA, partB);
            }
        }
    }

    /**
     * Compares two strings on the assumption that they have a numerical start
     * followed by lexicographical part.
     *
     * @return 0 if versions are equivalent, +ve if a is higher than b, -ve if a is
     * lower.
     */
    private static int compareVersionParts(@Nonnull String a, @Nonnull String b) {
        final int ai = findIndexOfFirstNonnumericalCharacter(a);
        final int bi = findIndexOfFirstNonnumericalCharacter(b);
        final String aNumberString;
        final String bNumberString;
        final String aRemainder;
        final String bRemainder;
        if (ai >= 0) {
            aNumberString = a.substring(0, ai);
            aRemainder = a.substring(ai);
        } else {
            aNumberString = a;
            aRemainder = "";
        }
        if (bi >= 0) {
            bNumberString = b.substring(0, bi);
            bRemainder = b.substring(bi);
        } else {
            bNumberString = b;
            bRemainder = "";
        }
        final long aNumber = aNumberString.isEmpty() ? -1L : Long.parseLong(aNumberString);
        final long bNumber = bNumberString.isEmpty() ? -1L : Long.parseLong(bNumberString);
        if (aNumber > bNumber) {
            return 1;
        }
        if (aNumber < bNumber) {
            return -1;
        }
        return aRemainder.compareTo(bRemainder);
    }

    private static int findIndexOfFirstNonnumericalCharacter(@Nonnull String s) {
        final int l = s.length();
        for (int i = 0; i < l; i++) {
            final char c = s.charAt(i);
            if (!Character.isDigit(c)) {
                return i;
            }
        }
        return -l;
    }
}
