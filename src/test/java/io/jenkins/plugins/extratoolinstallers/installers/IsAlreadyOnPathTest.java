package io.jenkins.plugins.extratoolinstallers.installers;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.SystemUtils;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import io.jenkins.plugins.extratoolinstallers.installers.FindOnPathCallable.ExecutableNotOnPathException;

/** Unit test for the {@link IsAlreadyOnPath} class. */
public class IsAlreadyOnPathTest {

    @Test
    public void defaultConstructorWhenCalledThenCreatesDefaultInstance() {
        // Given
        final String expectedLabel = "foobar";
        final String expectedExecutableName = null;
        final String expectedRelativePath = null;
        final IsAlreadyOnPath instance = new IsAlreadyOnPath(expectedLabel);

        // When
        final String actualLabel = instance.getLabel();
        final String actualExecutableName = instance.getExecutableName();
        final String actualRelativePath = instance.getRelativePath();

        // Then
        assertThat(actualLabel, equalTo(expectedLabel));
        assertThat(actualExecutableName, equalTo(expectedExecutableName));
        assertThat(actualRelativePath, equalTo(expectedRelativePath));
    }

    @Test
    public void getExecutableNameGivenEmptyValueThenReturnsNull() {
        // Given
        final String expectedLabel = "foobar";
        final String expectedExecutableName = null;
        final IsAlreadyOnPath instance = new IsAlreadyOnPath(expectedLabel);
        instance.setExecutableName("");

        // When
        final String actualExecutableName = instance.getExecutableName();

        // Then
        assertThat(actualExecutableName, equalTo(expectedExecutableName));
    }

    @Test
    public void getRelativePathGivenEmptyValueThenReturnsNull() {
        // Given
        final String expectedLabel = "foobar";
        final String expectedRelativePath = null;
        final IsAlreadyOnPath instance = new IsAlreadyOnPath(expectedLabel);
        instance.setRelativePath("");

        // When
        final String actualRelativePath = instance.getRelativePath();

        // Then
        assertThat(actualRelativePath, equalTo(expectedRelativePath));
    }

    @Test
    public void getExecutableNameGivenValueThenReturnsValue() {
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
    public void getRelativePathGivenValueThenReturnsValue() {
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
    public void performInstallationGivenNoExeThenThrows() throws Exception {
        // Given
        final Node mockNode = mock(Node.class);
        final ToolInstallation mockTool = mock(ToolInstallation.class);
        final List<String> actualLogRecord = newArrayList();
        final TaskListener mockLog = mockTaskListener(actualLogRecord);
        final String expectedExceptionMessage = Messages.IsAlreadyOnPath_executableNameIsEmpty();
        final String expectedLabel = "foobar";
        final TestFOPInstaller instance = new TestFOPInstaller(expectedLabel);
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
    public void performInstallationGivenAbsentExeThenThrows() throws Exception {
        // Given
        final Node mockNode = mock(Node.class);
        final FilePath nodeRootPath = new FilePath(new File(".."));
        when(mockNode.getRootPath()).thenReturn(nodeRootPath);
        final ToolInstallation mockTool = mock(ToolInstallation.class);
        final List<String> actualLogRecord = newArrayList();
        final TaskListener mockLog = mockTaskListener(actualLogRecord);
        final String expectedLabel = "foobar";
        final String expectedExecutableName = "someExeThatIsNotPresent";
        final IsAlreadyOnPath instance = new IsAlreadyOnPath(expectedLabel);
        instance.setExecutableName(expectedExecutableName);
        final String path = System.getenv("PATH");
        try {
            // When
            instance.performInstallation(mockTool, mockNode, mockLog);
            fail("Expected ExecutableNotOnPathException");

            // Then
        } catch (ExecutableNotOnPathException actual) {
            assertThat(actual.getExecutableName(), equalTo(expectedExecutableName));
            assertThat(actual.getPath(), equalTo(path));
        }
    }

    @Test
    public void performInstallationGivenExeIsntExecutableThenThrows() throws Exception {
        // Given
        assumeFalse("Can't test this on Windows as all files are executable", SystemUtils.IS_OS_WINDOWS);
        final TestFOPInstaller instance = new TestFOPInstaller("somelabel");
        final String executableName = "somevalue";
        instance.setExecutableName(executableName);
        final File exeParentDir = new File("tmpDir/");
        final FilePath expected = new FilePath(exeParentDir);
        try {
            // When
            doPerformInstallationSucceeds(instance, exeParentDir, expected, Boolean.TRUE, false);
            fail("Expected ExecutableNotOnPathException");

            // Then
        } catch (ExecutableNotOnPathException actual) {
            assertThat(actual.getExecutableName(), equalTo(executableName));
        }
    }

    @Test
    public void performInstallationGivenExeIsntFileThenThrows() throws Exception {
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
    public void performInstallationGivenFindableExeWithNoRelativePathThenReturnsItsDir() throws Exception {
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
    public void performInstallationGivenFindableExeWithRelativePathThenReturnsPathRelativeToItsDir() throws Exception {
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
    public void performInstallationGivenFindableExeWithAcceptableVersionThenReturnsPathRelativeToItsDir() throws Exception {
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
    public void performInstallationGivenFindableExeWithUnacceptableVersionThenThrows() throws Exception {
        // Given
        final TestFOPInstaller instance = new TestFOPInstaller("somelabel");
        instance.setExecutableName("someexe");
        instance.setVersionCmdString("someexe\n--version");
        instance.setVersionPatternString("Version (.*)");
        instance.setVersionMin("2.0");
        instance.setVersionMax("2.99");
        final File exeParentDir = new File("tmpDir/");
        final FilePath expected = new FilePath(exeParentDir);

        try {
            // When
            doPerformInstallationSucceeds(instance, exeParentDir, expected, Boolean.TRUE, true, instance.getVersionCmd(), "SomeExe\nVersion 1.2.3\nBuild 2022\n");
            fail("Expecting WrongVersionException");
        } catch ( WrongVersionException actual ) {
            // Then
            assertThat(actual.getDetectedVersion(), equalTo("1.2.3"));
            assertThat(actual.getMinVersion(), equalTo(instance.getVersionMin()));
            assertThat(actual.getMaxVersion(), equalTo(instance.getVersionMax()));
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
        final List<String> actualLogRecord = newArrayList();
        final TaskListener mockLog = mockTaskListener(actualLogRecord);
        final TestFOPCallable callable = new TestFOPCallable(expectedExecutableName, mockLog);
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
        if( versionExeCmd!=null ) {
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

    private static class TestFOPCallable extends FindOnPathCallable {
        private static final long serialVersionUID = 2L;

        private static interface IMock {
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
        private static interface IMock {
            FindOnPathCallable mkCallable(String exeName, TaskListener logOrNull);
            void launchCmd(String[] cmd, OutputStream output) throws IOException, InterruptedException;
        }

        public final IMock mock = mock(IMock.class);

        TestFOPInstaller(String label) {
            super(label);
        }

        @Override
        FindOnPathCallable mkCallable(String exeName, TaskListener logOrNull) {
            return mock.mkCallable(exeName, logOrNull);
        }

        @Override
        void runCommandOnNode(final Launcher launcher, final FilePath pwd, final String[] cmd,
                final OutputStream output) throws IOException, InterruptedException {
            mock.launchCmd(cmd, output);
        }
    }
}
