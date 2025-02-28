package io.jenkins.plugins.extratoolinstallers.installers;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import io.jenkins.plugins.extratoolinstallers.installers.FindOnPathCallable.ExecutableNotOnPathException;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit test for the {@link IsAlreadyOnPath} class. */
class IsAlreadyOnPathTest {

    @Test
    void defaultConstructorWhenCalledThenCreatesDefaultInstance() {
        // Given
        final String expectedLabel = "foobar";
        final IsAlreadyOnPath instance = new IsAlreadyOnPath(expectedLabel);

        // When
        final String actualLabel = instance.getLabel();
        final String actualExecutableName = instance.getExecutableName();
        final String actualRelativePath = instance.getRelativePath();

        // Then
        assertThat(actualLabel, equalTo(expectedLabel));
        assertThat(actualExecutableName, nullValue());
        assertThat(actualRelativePath, nullValue());
    }

    @Test
    void getExecutableNameGivenEmptyValueThenReturnsNull() {
        // Given
        final String expectedLabel = "foobar";
        final IsAlreadyOnPath instance = new IsAlreadyOnPath(expectedLabel);
        instance.setExecutableName("");

        // When
        final String actualExecutableName = instance.getExecutableName();

        // Then
        assertThat(actualExecutableName, nullValue());
    }

    @Test
    void getRelativePathGivenEmptyValueThenReturnsNull() {
        // Given
        final String expectedLabel = "foobar";
        final IsAlreadyOnPath instance = new IsAlreadyOnPath(expectedLabel);
        instance.setRelativePath("");

        // When
        final String actualRelativePath = instance.getRelativePath();

        // Then
        assertThat(actualRelativePath, nullValue());
    }

    @Test
    void getExecutableNameGivenValueThenReturnsValue() {
        // Given
        final String expectedLabel = "foobar";
        final String expectedExecutableName = "somevalue";
        final IsAlreadyOnPath instance = new IsAlreadyOnPath(expectedLabel);
        instance.setExecutableName(expectedExecutableName);

        // When
        final String actualExecutableName = instance.getExecutableName();

        // Then
        assertThat(actualExecutableName, equalTo(expectedExecutableName));
    }

    @Test
    void getRelativePathGivenValueThenReturnsValue() {
        // Given
        final String expectedLabel = "foobar";
        final String expectedRelativePath = "someothervalue";
        final IsAlreadyOnPath instance = new IsAlreadyOnPath(expectedLabel);
        instance.setRelativePath(expectedRelativePath);

        // When
        final String actualRelativePath = instance.getRelativePath();

        // Then
        assertThat(actualRelativePath, equalTo(expectedRelativePath));
    }

    @Test
    void performInstallationGivenNoExeThenThrows() {
        // Given
        final Node mockNode = mock(Node.class);
        final ToolInstallation mockTool = mock(ToolInstallation.class);
        final List<String> actualLogRecord = new ArrayList<>();
        final TaskListener mockLog = mockTaskListener(actualLogRecord);
        final String expectedExceptionMessage = Messages.IsAlreadyOnPath_executableNameIsEmpty();
        final String expectedLabel = "foobar";
        final TestFOPInstaller instance = new TestFOPInstaller(expectedLabel);

        // When
        final IllegalArgumentException actual = assertThrows(IllegalArgumentException.class,
                () -> instance.performInstallation(mockTool, mockNode, mockLog));

        // Then
        assertThat(actual.getMessage(), equalTo(expectedExceptionMessage));
    }

    @Test
    void performInstallationGivenAbsentExeThenThrows() {
        // Given
        final Node mockNode = mock(Node.class);
        final FilePath nodeRootPath = new FilePath(new File(".."));
        when(mockNode.getRootPath()).thenReturn(nodeRootPath);
        final ToolInstallation mockTool = mock(ToolInstallation.class);
        final List<String> actualLogRecord = new ArrayList<>();
        final TaskListener mockLog = mockTaskListener(actualLogRecord);
        final String expectedLabel = "foobar";
        final String expectedExecutableName = "someExeThatIsNotPresent";
        final IsAlreadyOnPath instance = new IsAlreadyOnPath(expectedLabel);
        instance.setExecutableName(expectedExecutableName);
        final String path = System.getenv("PATH");

        // When
        final ExecutableNotOnPathException actual = assertThrows(ExecutableNotOnPathException.class,
                () -> instance.performInstallation(mockTool, mockNode, mockLog));

        // Then
        assertThat(actual.getExecutableName(), equalTo(expectedExecutableName));
        assertThat(actual.getPath(), equalTo(path));
    }

    @Test
    void performInstallationGivenExeIsntExecutableThenThrows() {
        // Given
        assumeFalse(Functions.isWindows(), "Can't test this on Windows as all files are executable");
        final TestFOPInstaller instance = new TestFOPInstaller("somelabel");
        final String executableName = "somevalue";
        instance.setExecutableName(executableName);
        final File exeParentDir = new File("tmpDir/");
        final FilePath expected = new FilePath(exeParentDir);

        // When
        final ExecutableNotOnPathException actual = assertThrows(ExecutableNotOnPathException.class,
                () -> doPerformInstallationSucceeds(instance, exeParentDir, expected, Boolean.TRUE, false));

        // Then
        assertThat(actual.getExecutableName(), equalTo(executableName));
    }

    @Test
    void performInstallationGivenExeIsntFileThenThrows() throws Exception {
        // Given
        final TestFOPInstaller instance = new TestFOPInstaller("somelabel");
        final String executableName = "somevalue";
        instance.setExecutableName(executableName);
        final File exeParentDir = new File("tmpDir/");
        final FilePath expected = new FilePath(exeParentDir);
        try {
            // When
            doPerformInstallationSucceeds(instance, exeParentDir, expected, Boolean.FALSE, false);
            fail("Expected ExecutableNotOnPathException");

            // Then
        } catch (ExecutableNotOnPathException actual) {
            assertThat(actual.getExecutableName(), equalTo(executableName));
        }
    }

    @Test
    void performInstallationGivenFindableExeWithNoRelativePathThenReturnsItsDir() throws Exception {
        // Given
        final TestFOPInstaller instance = new TestFOPInstaller("somelabel");
        instance.setExecutableName("somevalue");
        final File exeParentDir = new File("tmpDir/");
        final FilePath expected = new FilePath(exeParentDir);
        // When
        final FilePath actual = doPerformInstallationSucceeds(instance, exeParentDir, expected, Boolean.TRUE, true);

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    void performInstallationGivenFindableExeWithRelativePathThenReturnsPathRelativeToItsDir() throws Exception {
        // Given
        final TestFOPInstaller instance = new TestFOPInstaller("somelabel");
        instance.setExecutableName("someexe");
        instance.setRelativePath("../foo");
        final File exeParentDir = new File("tmpDir/");
        final FilePath expected = new FilePath(exeParentDir).child("../foo");
        // When
        final FilePath actual = doPerformInstallationSucceeds(instance, exeParentDir, expected, Boolean.TRUE, true);

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    void performInstallationGivenFindableExeWithAcceptableVersionThenReturnsPathRelativeToItsDir() throws Exception {
        // Given
        final TestFOPInstaller instance = new TestFOPInstaller("somelabel");
        instance.setExecutableName("someexe");
        instance.setVersionCmdString("someexe\n--version");
        instance.setVersionPatternString("Version (.*)");
        instance.setVersionMin("1.1");
        instance.setVersionMax("1.99");
        final File exeParentDir = new File("tmpDir/");
        final FilePath expected = new FilePath(exeParentDir);

        // When
        final FilePath actual = doPerformInstallationSucceeds(instance, exeParentDir, expected, Boolean.TRUE, true, instance.getVersionCmd(), "SomeExe\nVersion 1.2.3\nBuild 2022\n");

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    void performInstallationGivenFindableExeWithUnacceptableVersionThenThrows() {
        // Given
        final TestFOPInstaller instance = new TestFOPInstaller("somelabel");
        instance.setExecutableName("someexe");
        instance.setVersionCmdString("someexe\n--version");
        instance.setVersionPatternString("Version (.*)");
        instance.setVersionMin("2.0");
        instance.setVersionMax("2.99");
        final File exeParentDir = new File("tmpDir/");
        final FilePath expected = new FilePath(exeParentDir);

            // When
        final WrongVersionException actual = assertThrows(WrongVersionException.class,
                () -> doPerformInstallationSucceeds(instance, exeParentDir, expected, Boolean.TRUE, true, instance.getVersionCmd(), "SomeExe\nVersion 1.2.3\nBuild 2022\n"));

        // Then
        assertThat(actual.getDetectedVersion(), equalTo("1.2.3"));
        assertThat(actual.getMinVersion(), equalTo(instance.getVersionMin()));
        assertThat(actual.getMaxVersion(), equalTo(instance.getVersionMax()));
    }

    @Test
    void parseVersionCmdOutputForVersionGivenNonMatchingPatternThenReturnsNull() {
        // Given
        final Pattern versionPattern = Pattern.compile("git version ([0-9.]*)");
        final String cmdOutput = "command\nnot\nfound";

        // When
        final String actual = IsAlreadyOnPath.parseVersionCmdOutputForVersion(versionPattern, cmdOutput);

        // Then
        assertThat(actual, nullValue());
    }

    @Test
    void parseVersionCmdOutputForVersionGivenMatchingPatternThenReturnsGroups() {
        // Given
        final Pattern versionPattern = Pattern.compile("git version ([0-9.]*)");
        final String cmdOutput = "git version 1.2.3\n";
        final String expected = "1.2.3";

        // When
        final String actual = IsAlreadyOnPath.parseVersionCmdOutputForVersion(versionPattern, cmdOutput);

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    void checkVersionIsInRangeGivenSimpleVersionWithinRangeThenReturnsZero() {
        // Given
        final String versionMin = "1.0.0";
        final String versionMax = "1.99";
        final String versionToBeChecked = "1.2.3";
        final int expected = 0;

        // When
        final int actual = IsAlreadyOnPath.checkVersionIsInRange(versionMin, versionMax, versionToBeChecked);

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    void checkVersionIsInRangeGivenVersionSlightlyBeyondRangeWithinRangeThenReturnsPositive() {
        // Given
        final String versionMin = "1.0.0";
        final String versionMax = "1.2.3";
        final String versionToBeChecked = "1.2.3A";
        final int expected = 1;

        // When
        final int actual = IsAlreadyOnPath.checkVersionIsInRange(versionMin, versionMax, versionToBeChecked);

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    void checkVersionIsInRangeGivenAllKindsOfVersionsThenReturnsAsExpected() {
        // Given
        final String[] versionsInOrder = { null, "A", "A.", "A.1", "A1", "B", "0.1", "1", "1.A", "1.2", "1.2.3.4",
                "1.2.3.4A", "1A", "2.something" };
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
                    final int actual = IsAlreadyOnPath.checkVersionIsInRange(versionMin, versionMax,
                            versionToBeChecked);
                    // Then
                    assertThat("Test that " + versionMin + "<=" + versionToBeChecked + "<=" + versionMax, actual,
                            equalTo(expected));
                }
            }
        }
    }

    private FilePath doPerformInstallationSucceeds(final TestFOPInstaller instance, final File exeParentDir,
            final FilePath expected, final Boolean mkFileNotDir, final boolean setExecutable)
            throws IOException, InterruptedException {
        return doPerformInstallationSucceeds(instance, exeParentDir, expected, mkFileNotDir, setExecutable, null, null);
    }

    private FilePath doPerformInstallationSucceeds(final TestFOPInstaller instance, final File exeParentDir,
            final FilePath expected, final Boolean mkFileNotDir, final boolean setExecutable, final String[] versionExeCmd, final String versionExeFakeOutput)
            throws IOException, InterruptedException {
        final String expectedExecutableName = instance.getExecutableName();
        final File exeFile = new File(exeParentDir, expectedExecutableName);
        final Node mockNode = mock(Node.class);
        final FilePath nodeRootPath = new FilePath(new File(".."));
        when(mockNode.getRootPath()).thenReturn(nodeRootPath);
        final ToolInstallation mockTool = mock(ToolInstallation.class);
        final List<String> actualLogRecord = new ArrayList<>();
        final TaskListener mockLog = mockTaskListener(actualLogRecord);
        final TestFOPCallable callable = new TestFOPCallable(expectedExecutableName, mockLog);
        exeParentDir.mkdirs();
        if (mkFileNotDir != null) {
            if (mkFileNotDir) {
                exeFile.createNewFile();
            } else {
                exeFile.mkdir();
            }
            if (setExecutable) {
                exeFile.setExecutable(true);
            }
        }
        if( versionExeCmd!=null ) {
            final Launcher mockLauncher = mock(Launcher.class);
            when(mockNode.createLauncher(mockLog)).thenReturn(mockLauncher);
            doAnswer((Answer<Void>) invocation -> {
                final OutputStream os = invocation.getArgument(1, OutputStream.class);
                os.write(versionExeFakeOutput.getBytes());
                return null;
            }).when(instance.mock).launchCmd(eq(versionExeCmd), any(OutputStream.class));
        }
        try {
            final FilePath exePath = expected.child(expectedExecutableName);
            final String originalPathBeforeTest = System.getenv("PATH");
            final String exeAbsolutePath = exeFile.getAbsolutePath();
            final String exeParentDirAbsolutePath = exeParentDir.getAbsolutePath();
            final String pathDuringTest = originalPathBeforeTest + File.pathSeparator + exeParentDirAbsolutePath;
            when(callable.mock.getPath()).thenReturn(pathDuringTest);
            when(instance.mock.mkCallable(expectedExecutableName, mockLog)).thenReturn(callable);
            when(mockNode.createPath(exeAbsolutePath)).thenReturn(exePath);

            // When
            return instance.performInstallation(mockTool, mockNode, mockLog);
        } finally {
            exeFile.delete();
            exeParentDir.delete();
        }
    }

    /////////////////////////////////////////////////////////////////
    // Test utility code.
    /////////////////////////////////////////////////////////////////

    /** Creates a {@link TaskListener} that records everything printed to it. */
    private static TaskListener mockTaskListener(List<String> whereToRecord) {
        final PrintStream ps = mock(PrintStream.class);
        doAnswer((Answer<Void>) invocation -> {
            final Object[] args = invocation.getArguments();
            final String arg = (String) args[0];
            whereToRecord.add(arg);
            return null;
        }).when(ps).println(anyString());
        final TaskListener tl = mock(TaskListener.class);
        when(tl.getLogger()).thenReturn(ps);
        return tl;
    }

    private static class TestFOPCallable extends FindOnPathCallable {
        @Serial
        private static final long serialVersionUID = 2L;

        private interface IMock {
            String getPath();
        }

        public final IMock mock = mock(IMock.class);

        TestFOPCallable(String executableName, TaskListener logOrNull) {
            super(executableName, logOrNull);
        }

        @Override
        String getPath() {
            return mock.getPath();
        }
    }

    private static class TestFOPInstaller extends IsAlreadyOnPath {
        private interface IMock {
            FindOnPathCallable mkCallable(String exeName, TaskListener logOrNull);
            void launchCmd(String[] cmd, OutputStream output);
        }

        public final IMock mock = mock(IMock.class);

        TestFOPInstaller(String label) {
            super(label);
        }

        @Override
        @NonNull
        FindOnPathCallable mkCallable(@NonNull String exeName, TaskListener logOrNull) {
            return mock.mkCallable(exeName, logOrNull);
        }

        @Override
        void runCommandOnNode(final Launcher launcher, final FilePath pwd, final String[] cmd,
                final OutputStream output) {
            mock.launchCmd(cmd, output);
        }
    }
}
