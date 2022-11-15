package io.jenkins.plugins.extratoolinstallers.installers;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.synopsys.arc.jenkinsci.plugins.extratoolinstallers.utils.ExtraToolInstallersException;

import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;

/** Unit test for the {@link AnyOfInstaller} class. */
public class AnyOfInstallerTest {

    @Test
    public void defaultConstructorWhenCalledThenCreatesDefaultInstance() {
        // Given
        final int expectedAttemptsOfWholeList = 1;
        final int expectedAttemptsPerInstaller = 1;
        final AnyOfInstaller instance = new AnyOfInstaller();

        // When
        final String actualLabel = instance.getLabel();
        final InstallSourceProperty actualInstallers = instance.getInstallers();
        final int actualAttemptsOfWholeList = instance.getAttemptsOfWholeList();
        final int actualAttemptsPerInstaller = instance.getAttemptsPerInstaller();

        // Then
        assertThat(actualLabel, nullValue());
        assertThat(actualInstallers, nullValue());
        assertThat(actualAttemptsOfWholeList, equalTo(expectedAttemptsOfWholeList));
        assertThat(actualAttemptsPerInstaller, equalTo(expectedAttemptsPerInstaller));
    }

    @Test
    public void setInstallersWhenCalledWhileToolSetThenSetsToolOnInstallers() throws Exception {
        // Given
        final TestToolInstaller installer1 = mock(TestToolInstaller.class);
        final TestToolInstaller installer2 = mock(TestToolInstaller.class);
        final List<ToolInstaller> installerList = Arrays.asList(installer1, installer2);
        final InstallSourceProperty installers = new InstallSourceProperty(installerList);
        final AnyOfInstaller instance = new AnyOfInstaller();
        final ToolInstallation mockToolInstallation = mock(ToolInstallation.class);
        instance.setTool(mockToolInstallation);

        // When
        instance.setInstallers(installers);

        // Then
        verify(installer1, times(1)).setTool(mockToolInstallation);
        verify(installer2, times(1)).setTool(mockToolInstallation);
    }

    @Test
    public void getAttemptsPerInstallerGivenZeroNumberThenReturnsOne() {
        // Given
        final int expectedAttemptsPerInstaller = 1;
        final AnyOfInstaller instance = new AnyOfInstaller();
        instance.setAttemptsPerInstaller(0);

        // When
        final int actualAttemptsPerInstaller = instance.getAttemptsPerInstaller();

        // Then
        assertThat(actualAttemptsPerInstaller, equalTo(expectedAttemptsPerInstaller));
    }

    @Test
    public void getAttemptsPerInstallerGivenNegativeNumberThenReturnsOne() {
        // Given
        final int expectedAttemptsPerInstaller = 1;
        final AnyOfInstaller instance = new AnyOfInstaller();
        instance.setAttemptsPerInstaller(-1234);

        // When
        final int actualAttemptsPerInstaller = instance.getAttemptsPerInstaller();

        // Then
        assertThat(actualAttemptsPerInstaller, equalTo(expectedAttemptsPerInstaller));
    }

    @Test
    public void getAttemptsOfWholeListGivenZeroNumberThenReturnsOne() {
        // Given
        final int expectedAttemptsOfWholeList = 1;
        final AnyOfInstaller instance = new AnyOfInstaller();
        instance.setAttemptsOfWholeList(0);

        // When
        final int actualAttemptsOfWholeList = instance.getAttemptsOfWholeList();

        // Then
        assertThat(actualAttemptsOfWholeList, equalTo(expectedAttemptsOfWholeList));
    }

    @Test
    public void getAttemptsOfWholeListGivenNegativeNumberThenReturnsOne() {
        // Given
        final int expectedAttemptsOfWholeList = 1;
        final AnyOfInstaller instance = new AnyOfInstaller();
        instance.setAttemptsOfWholeList(-1234);

        // When
        final int actualAttemptsOfWholeList = instance.getAttemptsOfWholeList();

        // Then
        assertThat(actualAttemptsOfWholeList, equalTo(expectedAttemptsOfWholeList));
    }

    @Test
    public void setToolWhenCalledNoInstancesThenJustSetsTool() {
        // Given
        final AnyOfInstaller instance = new AnyOfInstaller();
        final ToolInstallation mockToolInstallation = mock(ToolInstallation.class);

        // When
        instance.setTool(mockToolInstallation);

        // Then
        // expect no exception to be thrown
    }

    @Test
    public void setToolWhenCalledWithInstancesThenSetsToolOnThoseAsWell() throws Exception {
        // Given
        final TestToolInstaller installer1 = mock(TestToolInstaller.class);
        final TestToolInstaller installer2 = mock(TestToolInstaller.class);
        final List<ToolInstaller> installerList = Arrays.asList(installer1, installer2);
        final InstallSourceProperty installers = new InstallSourceProperty(installerList);
        final AnyOfInstaller instance = new AnyOfInstaller();
        instance.setInstallers(installers);
        final ToolInstallation mockToolInstallation = mock(ToolInstallation.class);

        // When
        instance.setTool(mockToolInstallation);

        // Then
        verify(installer1, times(1)).setTool(mockToolInstallation);
        verify(installer2, times(1)).setTool(mockToolInstallation);
    }

    @Test
    public void appliesToGivenNoInstallersThenReturnsFalse() {
        // Given
        final AnyOfInstaller instance = new AnyOfInstaller();
        final Node mockNode = mock(Node.class);

        // When
        final boolean actual = instance.appliesTo(mockNode);

        // Then
        assertThat(actual, equalTo(false));
    }

    @Test
    public void appliesToGivenNoApplicableInstallersThenReturnsFalse() throws Exception {
        // Given
        final Node mockNode = mock(Node.class);
        final ToolInstaller installer1 = mock(ToolInstaller.class);
        final ToolInstaller installer2 = mock(ToolInstaller.class);
        when(installer1.appliesTo(mockNode)).thenReturn(false);
        when(installer2.appliesTo(mockNode)).thenReturn(false);
        final List<ToolInstaller> installerList = Arrays.asList(installer1, installer2);
        final InstallSourceProperty installers = new InstallSourceProperty(installerList);
        final AnyOfInstaller instance = new AnyOfInstaller();
        instance.setInstallers(installers);

        // When
        final boolean actual = instance.appliesTo(mockNode);

        // Then
        assertThat(actual, equalTo(false));
    }

    @Test
    public void appliesToGivenOneApplicableInstallerThenReturnsTrue() throws Exception {
        // Given
        final Node mockNode = mock(Node.class);
        final ToolInstaller installer1 = mock(ToolInstaller.class);
        final ToolInstaller installer2 = mock(ToolInstaller.class);
        when(installer1.appliesTo(mockNode)).thenReturn(false);
        when(installer2.appliesTo(mockNode)).thenReturn(true);
        final List<ToolInstaller> installerList = Arrays.asList(installer1, installer2);
        final InstallSourceProperty installers = new InstallSourceProperty(installerList);
        final AnyOfInstaller instance = new AnyOfInstaller();
        instance.setInstallers(installers);

        // When
        final boolean actual = instance.appliesTo(mockNode);

        // Then
        assertThat(actual, equalTo(true));
    }

    @Test
    public void performInstallationGiven1Loop1FailingInstaller1AttemptThenFails() throws Exception {
        // Given
        final Node mockNode = mock(Node.class);
        final ToolInstallation mockTool = mock(ToolInstallation.class);
        final List<String> actualLogRecord = new ArrayList<>();
        final TaskListener mockLog = mockTaskListener(actualLogRecord);
        final String installerDisplayName = "MyInstaller";
        final ToolInstaller installer = mockInstaller(installerDisplayName, mockNode);
        final PretendInstallerFailureException expectedCause = new PretendInstallerFailureException();
        when(installer.performInstallation(mockTool, mockNode, mockLog)).thenThrow(expectedCause);
        final List<ToolInstaller> installerList = Collections.singletonList(installer);
        final InstallSourceProperty installers = new InstallSourceProperty(installerList);
        final AnyOfInstaller instance = new AnyOfInstaller();
        instance.setInstallers(installers);
        final List<String> expectedLogRecord = Collections.singletonList(Messages.AnyOfInstaller_1loop_1installer_1attempt(1, 1, 1,
                1, installerDisplayName, 1, 1, expectedCause));

        // When
        try {
            instance.performInstallation(mockTool, mockNode, mockLog);
            fail("Expected " + ExtraToolInstallersException.class);
        } catch (ExtraToolInstallersException ex) {
            // Then
            assertThat(ex.getCause(), sameInstance(expectedCause));
        }
        assertThat(actualLogRecord, equalTo(expectedLogRecord));
        verify(installer, times(1)).performInstallation(mockTool, mockNode, mockLog);
    }

    @Test
    public void performInstallationGivenOneLoopAFailingInapplicableInstallerAndAPassingApplicableInstallerOneAttemptThenPasses()
            throws Exception {
        // Given
        final Node mockNode = mock(Node.class);
        final ToolInstallation mockTool = mock(ToolInstallation.class);
        final List<String> actualLogRecord = new ArrayList<>();
        final TaskListener mockLog = mockTaskListener(actualLogRecord);
        final String workingInstallerDisplayName = "WorkingInstaller";
        final String failingInstallerDisplayName = "FailingInapplicableInstaller";
        final ToolInstaller applicableInstaller = mockInstaller(workingInstallerDisplayName, mockNode);
        final ToolInstaller inapplicableInstaller = mockInstaller(failingInstallerDisplayName);
        when(inapplicableInstaller.performInstallation(mockTool, mockNode, mockLog))
                .thenThrow(new PretendInstallerFailureException());
        final FilePath expected = stubFilePath();
        when(applicableInstaller.performInstallation(mockTool, mockNode, mockLog)).thenReturn(expected);
        final List<ToolInstaller> installerList = Arrays.asList(inapplicableInstaller, applicableInstaller);
        final InstallSourceProperty installers = new InstallSourceProperty(installerList);
        final AnyOfInstaller instance = new AnyOfInstaller();
        instance.setInstallers(installers);
        final List<String> expectedLogRecord = new ArrayList<>();

        // When
        final FilePath actual = instance.performInstallation(mockTool, mockNode, mockLog);

        // Then
        assertThat(actual, equalTo(expected));
        assertThat(actualLogRecord, equalTo(expectedLogRecord));
        verify(applicableInstaller, times(1)).performInstallation(mockTool, mockNode, mockLog);
        verify(inapplicableInstaller, times(0)).performInstallation(mockTool, mockNode, mockLog);
    }

    @Test
    public void performInstallationGivenTwoLoopsFourUnreliableInstallersThreeAttemptsThenEventuallyPasses()
            throws Exception {
        // Given
        final Node mockNode = mock(Node.class);
        final ToolInstallation mockTool = mock(ToolInstallation.class);
        final List<String> actualLogRecord = new ArrayList<>();
        final TaskListener mockLog = mockTaskListener(actualLogRecord);
        final String inapplicableInstallerDisplayName1 = "ShouldNotAppearAsThisIsNotApplicableToNode1";
        final String inapplicableInstallerDisplayName2 = "ShouldNotAppearAsThisIsNotApplicableToNode2";
        final ToolInstaller inapplicableInstaller1 = mockInstaller(inapplicableInstallerDisplayName1);
        final ToolInstaller inapplicableInstaller2 = mockInstaller(inapplicableInstallerDisplayName2);
        final String failingInstallerName = "AlwaysFails";
        final PretendInstallerFailureException failingInstallerCause1 = new PretendInstallerFailureException(
                "failingInstallerCause1");
        final PretendInstallerFailureException failingInstallerCause2 = new PretendInstallerFailureException(
                "failingInstallerCause2");
        final PretendInstallerFailureException failingInstallerCause3 = new PretendInstallerFailureException(
                "failingInstallerCause3");
        final PretendInstallerFailureException failingInstallerCause4 = new PretendInstallerFailureException(
                "failingInstallerCause4");
        final PretendInstallerFailureException failingInstallerCause5 = new PretendInstallerFailureException(
                "failingInstallerCause5");
        final PretendInstallerFailureException failingInstallerCause6 = new PretendInstallerFailureException(
                "failingInstallerCause6");
        final ToolInstaller failingInstaller = mockInstaller(failingInstallerName, mockNode);
        when(failingInstaller.performInstallation(mockTool, mockNode, mockLog)).thenThrow(failingInstallerCause1,
                failingInstallerCause2, failingInstallerCause3, failingInstallerCause4, failingInstallerCause5,
                failingInstallerCause6);
        final String unreliableInstallerName = "FailsFirst4TimesThenPasses";
        final FilePath expected = stubFilePath();
        final PretendInstallerFailureException unreliableInstallerCause1 = new PretendInstallerFailureException(
                "unreliableInstallerCause1");
        final PretendInstallerFailureException unreliableInstallerCause2 = new PretendInstallerFailureException(
                "unreliableInstallerCause2");
        final PretendInstallerFailureException unreliableInstallerCause3 = new PretendInstallerFailureException(
                "unreliableInstallerCause3");
        final PretendInstallerFailureException unreliableInstallerCause4 = new PretendInstallerFailureException(
                "unreliableInstallerCause4");
        final ToolInstaller unreliableInstaller = mockInstaller(unreliableInstallerName, mockNode);
        when(unreliableInstaller.performInstallation(mockTool, mockNode, mockLog)).thenThrow(unreliableInstallerCause1,
                unreliableInstallerCause2, unreliableInstallerCause3, unreliableInstallerCause4).thenReturn(expected);
        final List<ToolInstaller> installerList = Arrays.asList(inapplicableInstaller1, failingInstaller,
                inapplicableInstaller2, unreliableInstaller);
        final int failingInstallerIndex = 2;
        final int unreliableInstallerIndex = 4;
        final InstallSourceProperty installers = new InstallSourceProperty(installerList);
        final AnyOfInstaller instance = new AnyOfInstaller();
        instance.setInstallers(installers);
        final int loops = 2;
        final int tries = 3;
        final int insts = installerList.size();
        instance.setAttemptsOfWholeList(loops);
        instance.setAttemptsPerInstaller(tries);
        final List<String> expectedLogRecord = Arrays.asList(
                Messages.AnyOfInstaller_loops_installers_attempts(1, loops, failingInstallerIndex, insts,
                        failingInstallerName, 1, tries, failingInstallerCause1),
                Messages.AnyOfInstaller_loops_installers_attempts(1, loops, failingInstallerIndex, insts,
                        failingInstallerName, 2, tries, failingInstallerCause2),
                Messages.AnyOfInstaller_loops_installers_attempts(1, loops, failingInstallerIndex, insts,
                        failingInstallerName, 3, tries, failingInstallerCause3),
                Messages.AnyOfInstaller_loops_installers_attempts(1, loops, unreliableInstallerIndex, insts,
                        unreliableInstallerName, 1, tries, unreliableInstallerCause1),
                Messages.AnyOfInstaller_loops_installers_attempts(1, loops, unreliableInstallerIndex, insts,
                        unreliableInstallerName, 2, tries, unreliableInstallerCause2),
                Messages.AnyOfInstaller_loops_installers_attempts(1, loops, unreliableInstallerIndex, insts,
                        unreliableInstallerName, 3, tries, unreliableInstallerCause3),
                Messages.AnyOfInstaller_loops_installers_attempts(2, loops, failingInstallerIndex, insts,
                        failingInstallerName, 1, tries, failingInstallerCause4),
                Messages.AnyOfInstaller_loops_installers_attempts(2, loops, failingInstallerIndex, insts,
                        failingInstallerName, 2, tries, failingInstallerCause5),
                Messages.AnyOfInstaller_loops_installers_attempts(2, loops, failingInstallerIndex, insts,
                        failingInstallerName, 3, tries, failingInstallerCause6),
                Messages.AnyOfInstaller_loops_installers_attempts(2, loops, unreliableInstallerIndex, insts,
                        unreliableInstallerName, 1, tries, unreliableInstallerCause4));

        // When
        final FilePath actual = instance.performInstallation(mockTool, mockNode, mockLog);

        // Then
        assertThat(actual, equalTo(expected));
        assertThat(actualLogRecord, equalTo(expectedLogRecord));
        verify(failingInstaller, times(6)).performInstallation(mockTool, mockNode, mockLog);
        verify(unreliableInstaller, times(5)).performInstallation(mockTool, mockNode, mockLog);
        verify(inapplicableInstaller1, times(0)).performInstallation(mockTool, mockNode, mockLog);
        verify(inapplicableInstaller2, times(0)).performInstallation(mockTool, mockNode, mockLog);
    }

    /////////////////////////////////////////////////////////////////
    // Test utility code.
    /////////////////////////////////////////////////////////////////

    /**
     * Creates a {@link FilePath} we can return from a successful installation.
     */
    private static FilePath stubFilePath() {
        return new FilePath(new File("/"));
    }

    /**
     * Creates a {@link ToolInstaller} with a given name whose
     * {@link ToolInstaller#appliesTo(Node)} answers "true" to the specified
     * nodes.
     */
    private static ToolInstaller mockInstaller(String displayName, Node... nodesThisAppliesTo) {
        // use deep stub to avoid generic type code warnings
        final ToolInstaller ti = mock(ToolInstaller.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS));
        when(ti.getDescriptor().getDisplayName()).thenReturn(displayName);
        for (final Node node : nodesThisAppliesTo) {
            when(ti.appliesTo(node)).thenReturn(true);
        }
        return ti;
    }

    /** Creates a {@link TaskListener} that records everything printed to it. */
    private static TaskListener mockTaskListener(List<String> whereToRecord) {
        final PrintStream ps = mock(PrintStream.class);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
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

    /** Makes {@link #setTool(ToolInstallation)} visible to this test. */
    private static abstract class TestToolInstaller extends ToolInstaller {
        public TestToolInstaller(String label) {
            super(label);
        }

        @Override
        public void setTool(ToolInstallation t) {
            super.setTool(t);
        }
    }

    /** Used to test installation failures. */
    private static class PretendInstallerFailureException extends IOException {
        private static final long serialVersionUID = 1L;

        PretendInstallerFailureException() {
            super();
        }

        PretendInstallerFailureException(String message) {
            super(message);
        }
    }
}
