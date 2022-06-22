package io.jenkins.plugins.extratoolinstallers.installers;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import io.jenkins.plugins.extratoolinstallers.installers.FindInDirCallable.ExecutableNotFoundException;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.regex.Pattern;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for the {@link IsAlreadyInGivenDirectory} class.
 */
public class IsAlreadyInGivenDirectoryTest {

    /**
     * Creates a {@link TaskListener} that records everything printed to it.
     */
    private static TaskListener mockTaskListener(List<String> whereToRecord) {
        final PrintStream ps = mock(PrintStream.class);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                final Object[] args = invocation.getArguments();
                final String arg = (String) args[0];
                whereToRecord.add(arg);
                return null;
            }
        }).when(ps).println(anyString());
        final TaskListener tl = mock(TaskListener.class);
        when(tl.getLogger()).thenReturn(ps);
        return tl;
    }

    @Test
    public void defaultConstructorWhenCalledThenCreatesDefaultInstance() {
        // Given
        final String expectedLabel = "foobar";
        final String expectedExecutablePath = null;
        final String expectedRelativePath = null;
        final IsAlreadyInGivenDirectory instance = new IsAlreadyInGivenDirectory(expectedLabel);

        // When
        final String actualLabel = instance.getLabel();
        final String actualExecutablePath = instance.getExecutablePath();
        final String actualRelativePath = instance.getRelativePath();

        // Then
        assertThat(actualLabel, equalTo(expectedLabel));
        assertThat(actualExecutablePath, equalTo(expectedExecutablePath));
        assertThat(actualRelativePath, equalTo(expectedRelativePath));
    }

    @Test
    public void getExecutablePathGivenEmptyValueThenReturnsNull() {
        // Given
        final String expectedLabel = "foobar";
        final String expectedExecutablePath = null;
        final IsAlreadyInGivenDirectory instance = new IsAlreadyInGivenDirectory(expectedLabel);
        instance.setExecutablePath("");

        // When
        final String actualExecutablePath = instance.getExecutablePath();

        // Then
        assertThat(actualExecutablePath, equalTo(expectedExecutablePath));
    }

    @Test
    public void getRelativePathGivenEmptyValueThenReturnsNull() {
        // Given
        final String expectedLabel = "foobar";
        final String expectedRelativePath = null;
        final IsAlreadyInGivenDirectory instance = new IsAlreadyInGivenDirectory(expectedLabel);
        instance.setRelativePath("");

        // When
        final String actualRelativePath = instance.getRelativePath();

        // Then
        assertThat(actualRelativePath, equalTo(expectedRelativePath));
    }

    @Test
    public void getExecutablePathGivenValueThenReturnsValue() {
        // Given
        final String expectedLabel = "foobar";
        final String expectedExecutablePath = "somevalue";
        final IsAlreadyInGivenDirectory instance = new IsAlreadyInGivenDirectory(expectedLabel);
        instance.setExecutablePath(expectedExecutablePath);

        // When
        final String actualExecutablePath = instance.getExecutablePath();

        // Then
        assertThat(actualExecutablePath, equalTo(expectedExecutablePath));
    }

    @Test
    public void getRelativePathGivenValueThenReturnsValue() {
        // Given
        final String expectedLabel = "foobar";
        final String expectedRelativePath = "someothervalue";
        final IsAlreadyInGivenDirectory instance = new IsAlreadyInGivenDirectory(expectedLabel);
        instance.setRelativePath(expectedRelativePath);

        // When
        final String actualRelativePath = instance.getRelativePath();

        // Then
        assertThat(actualRelativePath, equalTo(expectedRelativePath));
    }

    @Test
    public void performInstallationGivenNoExeThenThrows() throws Exception {
        // Given
        final Node mockNode = mock(Node.class);
        final ToolInstallation mockTool = mock(ToolInstallation.class);
        final List<String> actualLogRecord = newArrayList();
        final TaskListener mockLog = mockTaskListener(actualLogRecord);
        final String expectedExceptionMessage = Messages.IsAlreadyInGivenDirectory_executablePathIsEmpty();
        final String expectedLabel = "foobar";
        final TestFIDInstaller instance = new TestFIDInstaller(expectedLabel);
        try {
            // When
            instance.performInstallation(mockTool, mockNode, mockLog);
            fail("Expected IllegalArgumentException");

            // Then
        } catch (IllegalArgumentException actual) {
            assertThat(actual.getMessage(), equalTo(expectedExceptionMessage));
        }
    }

    @Test
    public void performInstallationGivenAbsentFilePathThenThrows() throws Exception {
        // Given
        final Node mockNode = mock(Node.class);
        final FilePath nodeRootPath = new FilePath(new File("."));
        when(mockNode.getRootPath()).thenReturn(nodeRootPath);
        final ToolInstallation mockTool = mock(ToolInstallation.class);
        final List<String> actualLogRecord = newArrayList();
        final TaskListener mockLog = mockTaskListener(actualLogRecord);
        final String expectedLabel = "foobar";
        final String expectedExecutablePath = "/some/path/someExeThatIsNotPresent";
        final IsAlreadyInGivenDirectory instance = new IsAlreadyInGivenDirectory(expectedLabel);
        instance.setExecutablePath(expectedExecutablePath);
        final String path = System.getenv("PATH");
        try {
            // When
            instance.performInstallation(mockTool, mockNode, mockLog);
            fail("Expected ExecutableNotFoundException");

            // Then
        } catch (ExecutableNotFoundException actual) {
            assertThat(actual.getExecutablePath(), equalTo(expectedExecutablePath));
        }
    }

    @Test
    public void performInstallationGivenExeIsntFileThenThrows() throws Exception {
        // Given
        final TestFIDInstaller instance = new TestFIDInstaller("somelabel");
        final FilePath expected = new FilePath(new File("somepath/", "someDirectory")).absolutize();
        final String executablePath = expected.toString();
        instance.setExecutablePath(executablePath);
        try {
            // When
            doPerformInstallationSucceeds(instance, expected, Boolean.FALSE, false);
            fail("Expected ExecutableNotOnPathException");

            // Then
        } catch (ExecutableNotFoundException actual) {
            assertThat(actual.getExecutablePath(), equalTo(executablePath));
        }
    }

    @Test
    public void performInstallationGivenFindableExeWithNoRelativePathThenReturnsItsDir() throws Exception {
        // Given
        final TestFIDInstaller instance = new TestFIDInstaller("somelabel");
        final FilePath executablePath = new FilePath(new File("somepath/", "someFile")).absolutize();
        final FilePath expected = executablePath.getParent();
        instance.setExecutablePath(executablePath.toString());
        // When
        final FilePath actual = doPerformInstallationSucceeds(instance, executablePath, Boolean.TRUE, true);

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void performInstallationGivenFindableExeWithRelativePathThenReturnsPathRelativeToItsDir() throws Exception {
        // Given
        final TestFIDInstaller instance = new TestFIDInstaller("somelabel");
        final File executablePath = new File("some/path/", "someFile").getAbsoluteFile();
        final File exeParentDir = new File("some/path/").getAbsoluteFile();
        instance.setExecutablePath(executablePath.toString());
        String relativePath = "../foo";
        instance.setRelativePath(relativePath);
        final FilePath expected = new FilePath(exeParentDir).child("../foo");
        // When
        final FilePath actual = doPerformInstallationSucceeds(instance, expected, Boolean.TRUE, true);

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void performInstallationGivenFindableExeWithAcceptableVersionThenReturnsPathRelativeToItsDir() throws Exception {
        // Given
        final TestFIDInstaller instance = new TestFIDInstaller("somelabel");
        final FilePath executablePath = new FilePath(new File("somepath/", "someexe")).absolutize();
        instance.setExecutablePath(executablePath.toString());
        instance.setVersionCmdString(executablePath + "\n--version");
        instance.setVersionPatternString("Version (.*)");
        instance.setVersionMin("1.1");
        instance.setVersionMax("1.99");
        final FilePath expected = executablePath.getParent();

        // When
        final FilePath actual = doPerformInstallationSucceeds(instance, expected, Boolean.TRUE, true, instance.getVersionCmd(), "SomeExe\nVersion 1.2.3\nBuild 2022\n");

        // Then
        assertThat(actual, equalTo(expected));
    }

    @Test
    public void performInstallationGivenFindableExeWithUnacceptableVersionThenThrows() throws Exception {
        // Given
        final TestFIDInstaller instance = new TestFIDInstaller("somelabel");
        final FilePath executablePath = new FilePath(new File("somepath/", "someexe")).absolutize();
        instance.setExecutablePath(executablePath.toString());
        instance.setVersionCmdString(executablePath + "\n--version");
        instance.setVersionPatternString("Version (.*)");
        instance.setVersionMin("2.0");
        instance.setVersionMax("2.99");
        final File exeParentDir = new File("somepath/");
        final FilePath expected = new FilePath(exeParentDir);

        try {
            // When
            doPerformInstallationSucceeds(instance, expected, Boolean.TRUE, true, instance.getVersionCmd(), "SomeExe\nVersion 1.2.3\nBuild 2022\n");
            fail("Expecting WrongVersionException");
        } catch (WrongVersionException actual) {
            // Then
            assertThat(actual.getDetectedVersion(), equalTo("1.2.3"));
            assertThat(actual.getMinVersion(), equalTo(instance.getVersionMin()));
            assertThat(actual.getMaxVersion(), equalTo(instance.getVersionMax()));
        }
    }

    @Test
    public void parseVersionCmdOutputForVersionGivenNonMatchingPatternThenReturnsNull() throws Exception {
        // Given
        final Pattern versionPattern = Pattern.compile("git version ([0-9.]*)");
        final String cmdOutput = "command\nnot\nfound";
        final String expected = null;

        // When
        final String actual = IsAlreadyInGivenDirectory.parseVersionCmdOutputForVersion(versionPattern, cmdOutput);

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
        final String actual = IsAlreadyInGivenDirectory.parseVersionCmdOutputForVersion(versionPattern, cmdOutput);

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
        final int actual = IsAlreadyInGivenDirectory.checkVersionIsInRange(versionMin, versionMax, versionToBeChecked);

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
        final int actual = IsAlreadyInGivenDirectory.checkVersionIsInRange(versionMin, versionMax, versionToBeChecked);

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
                    final int actual = IsAlreadyInGivenDirectory.checkVersionIsInRange(versionMin, versionMax,
                            versionToBeChecked);
                    // Then
                    assertThat("Test that " + versionMin + "<=" + versionToBeChecked + "<=" + versionMax, actual,
                            equalTo(expected));
                }
            }
        }
    }


    @Test
    public void performInstallationGivenExeIsntExecutableThenThrows() throws Exception {
        // Given
        assumeFalse("Can't test this on Windows as all files are executable", SystemUtils.IS_OS_WINDOWS);
        final TestFIDInstaller instance = new TestFIDInstaller("somelabel");
        final FilePath expected = new FilePath(new File("somepath/", "someNotExecutableFile")).absolutize();
        final String executablePath = expected.toString();
        instance.setExecutablePath(executablePath);
        try {
            // When
            doPerformInstallationSucceeds(instance, expected, Boolean.TRUE, false);
            fail("Expected ExecutableNotOnPathException");

            // Then
        } catch (ExecutableNotFoundException actual) {
            assertThat(actual.getExecutablePath(), equalTo(executablePath));
        }
    }

    /////////////////////////////////////////////////////////////////
    // Test utility code.
    /////////////////////////////////////////////////////////////////

    private FilePath doPerformInstallationSucceeds(final TestFIDInstaller instance,
                                                   final FilePath expected, final Boolean mkFileNotDir, final boolean setExecutable)
            throws IOException, InterruptedException {
        return doPerformInstallationSucceeds(instance, expected, mkFileNotDir, setExecutable, null, null);
    }

    private FilePath doPerformInstallationSucceeds(final TestFIDInstaller instance,
                                                   final FilePath expected, final Boolean mkFileNotDir, final boolean setExecutable, final String[] versionExeCmd, final String versionExeFakeOutput)
            throws IOException, InterruptedException {
        final String expectedExecutablePath = instance.getExecutablePath();
        final File exeFile = new File(expectedExecutablePath);
        final Node mockNode = mock(Node.class);
        final FilePath nodeRootPath = new FilePath(new File("."));
        when(mockNode.getRootPath()).thenReturn(nodeRootPath);
        final ToolInstallation mockTool = mock(ToolInstallation.class);
        final List<String> actualLogRecord = newArrayList();
        final TaskListener mockLog = mockTaskListener(actualLogRecord);
        final TestFIDCallable callable = new TestFIDCallable(expectedExecutablePath, mockLog);
        final File exeParentDir = new File(exeFile.getParent());
        exeParentDir.mkdirs();
        if (mkFileNotDir != null) {
            if (mkFileNotDir.booleanValue()) {
                exeFile.createNewFile();
            } else {
                exeFile.mkdir();
            }
            if (setExecutable) {
                exeFile.setExecutable(true);
            }
        }
        if (versionExeCmd != null) {
            final Launcher mockLauncher = mock(Launcher.class);
            when(mockNode.createLauncher(mockLog)).thenReturn(mockLauncher);
            doAnswer(new Answer<Void>() {
                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                    final OutputStream os = invocation.getArgument(1, OutputStream.class);
                    os.write(versionExeFakeOutput.getBytes());
                    return null;
                }
            }).when(instance.mock).launchCmd(eq(versionExeCmd), any(OutputStream.class));
        }
        try {
            final FilePath exePath = new FilePath(exeFile);
            final String exeAbsolutePath = exeFile.getAbsolutePath();
            when(instance.mock.mkCallable(expectedExecutablePath, mockLog)).thenReturn(callable);
            when(mockNode.createPath(exeAbsolutePath)).thenReturn(exePath);

            // When
            return instance.performInstallation(mockTool, mockNode, mockLog);
        } finally {
            exeFile.delete();
            exeParentDir.delete();
        }
    }

    private static class TestFIDCallable extends FindInDirCallable {
        private static final long serialVersionUID = 2L;
        public final IMock mock = mock(IMock.class);

        TestFIDCallable(String executablePath, TaskListener logOrNull) {
            super(executablePath, logOrNull);
        }

        private interface IMock {
            String getExecutablePath();
        }

    }

    private static class TestFIDInstaller extends IsAlreadyInGivenDirectory {
        public final IMock mock = mock(IMock.class);

        TestFIDInstaller(String label) {
            super(label);
        }

        @Override
        FindInDirCallable mkCallable(String exePath, TaskListener logOrNull) {
            return mock.mkCallable(exePath, logOrNull);
        }

        @Override
        void runCommandOnNode(final Launcher launcher, final FilePath pwd, final String[] cmd,
                              final OutputStream output) throws IOException, InterruptedException {
            mock.launchCmd(cmd, output);
        }

        private interface IMock {
            FindInDirCallable mkCallable(String exePath, TaskListener logOrNull);

            void launchCmd(String[] cmd, OutputStream output) throws IOException, InterruptedException;
        }
    }
}
