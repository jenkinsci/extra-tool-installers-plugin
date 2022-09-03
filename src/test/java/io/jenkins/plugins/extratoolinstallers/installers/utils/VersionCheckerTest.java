package io.jenkins.plugins.extratoolinstallers.installers.utils;

import org.junit.Test;

import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for the {@link VersionChecker} class.
 */
public class VersionCheckerTest {
    @Test
    public void parseVersionCmdOutputForVersionGivenNonMatchingPatternThenReturnsNull() throws Exception {
        // Given
        final Pattern versionPattern = Pattern.compile("git version ([0-9.]*)");
        final String cmdOutput = "command\nnot\nfound";
        final String expected = null;

        // When
        final String actual = VersionChecker.parseVersionCmdOutputForVersion(versionPattern, cmdOutput);

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void parseVersionCmdOutputForVersionGivenMatchingPatternThenReturnsGroups() throws Exception {
        // Given
        final Pattern versionPattern = Pattern.compile("git version ([0-9.]*)");
        final String cmdOutput = "git version 1.2.3\n";
        final String expected = "1.2.3";

        // When
        final String actual = VersionChecker.parseVersionCmdOutputForVersion(versionPattern, cmdOutput);

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void checkVersionIsInRangeGivenSimpleVersionWithinRangeThenReturnsZero() throws Exception {
        // Given
        final String versionMin = "1.0.0";
        final String versionMax = "1.99";
        final String versionToBeChecked = "1.2.3";
        final int expected = 0;

        // When
        final int actual = VersionChecker.checkVersionIsInRange(versionMin, versionMax, versionToBeChecked);

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void checkVersionIsInRangeGivenVersionSlightlyBeyondRangeWithinRangeThenReturnsPositive() throws Exception {
        // Given
        final String versionMin = "1.0.0";
        final String versionMax = "1.2.3";
        final String versionToBeChecked = "1.2.3A";
        final int expected = 1;

        // When
        final int actual = VersionChecker.checkVersionIsInRange(versionMin, versionMax, versionToBeChecked);

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void checkVersionIsInRangeGivenAllKindsOfVersionsThenReturnsAsExpected() throws Exception {
        // Given
        final String[] versionsInOrder = {null, "A", "A.", "A.1", "A1", "B", "0.1", "1", "1.A", "1.2", "1.2.3.4",
                "1.2.3.4A", "1A", "2.something"};
        final int versionsLen = versionsInOrder.length;

        for (int iMin = 0; iMin < versionsLen; iMin++) {
            final String versionMin = versionsInOrder[iMin];
            for (int iMax = 0; iMax < versionsLen; iMax++) {
                final String versionMax = versionsInOrder[iMax];
                if (versionMin == null && versionMax == null) {
                    continue;
                }
                for (int iVer = 0; iVer < versionsLen; iVer++) {
                    final String versionToBeChecked = versionsInOrder[iVer];
                    if (versionToBeChecked == null) {
                        // continue;
                    }
                    final int expected;
                    if (versionMin != null && iMin > iVer) {
                        expected = -1;
                    } else if (versionMax != null && iMax < iVer) {
                        expected = 1;
                    } else {
                        expected = 0;
                    }
                    // When
                    final int actual = VersionChecker.checkVersionIsInRange(versionMin, versionMax,
                            versionToBeChecked);
                    // Then
                    assertThat("Test that " + versionMin + "<=" + versionToBeChecked + "<=" + versionMax, actual,
                            equalTo(expected));
                }
            }
        }
    }



}
