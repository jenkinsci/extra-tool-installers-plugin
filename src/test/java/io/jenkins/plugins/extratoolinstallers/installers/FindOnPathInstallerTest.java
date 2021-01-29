package io.jenkins.plugins.extratoolinstallers.installers;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.apache.commons.lang3.SystemUtils;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import io.jenkins.plugins.extratoolinstallers.installers.FindOnPathCallable.ExecutableNotOnPathException;

/** Unit test for the {@link FindOnPathInstaller} class. */
public class FindOnPathInstallerTest {

    @Test
    public void defaultConstructorWhenCalledThenCreatesDefaultInstance() {
        // Given
        final String expectedLabel = "foobar";
        final String expectedExecutableName = null;
        final String expectedRelativePath = null;
        final FindOnPathInstaller instance = new FindOnPathInstaller(expectedLabel);

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
        final FindOnPathInstaller instance = new FindOnPathInstaller(expectedLabel);
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
        final FindOnPathInstaller instance = new FindOnPathInstaller(expectedLabel);
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
        final FindOnPathInstaller instance = new FindOnPathInstaller(expectedLabel);
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
        final FindOnPathInstaller instance = new FindOnPathInstaller(expectedLabel);
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
        final String expectedExceptionMessage = Messages.FindOnPathInstaller_executableNameIsEmpty();
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
        final FindOnPathInstaller instance = new FindOnPathInstaller(expectedLabel);
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

    private FilePath doPerformInstallationSucceeds(final TestFOPInstaller instance, final File exeParentDir,
            final FilePath expected, final Boolean mkFileNotDir, final boolean setExecutable)
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

    private static class TestFOPInstaller extends FindOnPathInstaller {
        private static interface IMock {
            FindOnPathCallable mkCallable(String exeName, TaskListener logOrNull);
        }

        public final IMock mock = mock(IMock.class);

        TestFOPInstaller(String label) {
            super(label);
        }

        @Override
        FindOnPathCallable mkCallable(String exeName, TaskListener logOrNull) {
            return mock.mkCallable(exeName, logOrNull);
        }

    }
}
