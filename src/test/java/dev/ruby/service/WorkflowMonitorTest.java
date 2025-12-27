package dev.ruby.service;

import java.io.File;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.ruby.client.GitHubClient;
import dev.ruby.client.dto.WorkflowRun;
import dev.ruby.persistence.MonitorState;
import dev.ruby.persistence.StateManager;

@ExtendWith(MockitoExtension.class)
class WorkflowMonitorTest {

        @Mock
        private GitHubClient mockClient;

        @Mock
        private StateManager mockStateManager;

        private File stateFile;

        @BeforeEach
        void setUp() {
                stateFile = new File("test-repo-workflow-state.json");
        }

        @AfterEach
        void tearDown() {
                if (stateFile.exists()) {
                        stateFile.delete();
                }
        }

        @Test
        void run_shouldUpdateLastRunTime() throws Exception {
                Instant now = Instant.now();
                Instant futureTime = now.plusSeconds(100);

                WorkflowRun run = new WorkflowRun(123L, "Build", "queued", null,
                                "main", "abc1234567890",
                                futureTime, futureTime.plusSeconds(1), futureTime);

                StateManager realStateManager = new StateManager("test-repo");
                when(mockClient.getWorkflowRuns(1, 100)).thenReturn(List.of(run));
                when(mockClient.getWorkflowRuns(2, 100)).thenReturn(Collections.emptyList());

                WorkflowMonitor monitor = new WorkflowMonitor(mockClient, realStateManager);
                Instant initialTime = monitor.getState().getLastRunTime();

                monitor.run();

                assertTrue(monitor.getState().getLastRunTime().isAfter(initialTime));
        }

        @Test
        void run_shouldTrackActiveRuns() throws Exception {
                Instant now = Instant.now();
                WorkflowRun inProgressRun = new WorkflowRun(
                                123L, "Build", "in_progress", null,
                                "main", "abc1234567890",
                                now, now.plusSeconds(1), now);

                when(mockStateManager.load()).thenReturn(new MonitorState());
                when(mockClient.getWorkflowRuns(1, 100))
                                .thenReturn(List.of(inProgressRun))
                                .thenReturn(Collections.emptyList());
                when(mockClient.getWorkflowRuns(2, 100)).thenReturn(Collections.emptyList());
                when(mockClient.getJobsForRun(123L)).thenReturn(Collections.emptyList());

                WorkflowMonitor monitor = new WorkflowMonitor(mockClient, mockStateManager);
                monitor.run();

                MonitorState state = monitor.getState();
                assertTrue(state.getAlreadySeenKeys().keySet().stream()
                                .anyMatch(k -> k.contains("123") && k.contains("STARTED")));

                // second run: state poll active runs and transition state to SUCCESS
                WorkflowRun completedRun = new WorkflowRun(
                                123L, "Build", "completed", "success",
                                "main", "abc1234567890",
                                now, now.plusSeconds(60), now);

                when(mockClient.getWorkflowRun(123L)).thenReturn(completedRun);

                monitor.run();

                assertTrue(state.getAlreadySeenKeys().keySet().stream()
                                .anyMatch(k -> k.contains("123") && k.contains("SUCCESS")));
        }

        @Test
        void run_shouldClearActiveRuns() throws Exception {
                Instant now = Instant.now();
                WorkflowRun inProgressRun = new WorkflowRun(
                                123L, "Build", "in_progress", null,
                                "main", "abc1234567890",
                                now, now.plusSeconds(1), now);

                when(mockStateManager.load()).thenReturn(new MonitorState());
                when(mockClient.getWorkflowRuns(1, 100))
                                .thenReturn(List.of(inProgressRun))
                                .thenReturn(Collections.emptyList());
                when(mockClient.getWorkflowRuns(2, 100)).thenReturn(Collections.emptyList());
                when(mockClient.getJobsForRun(123L)).thenReturn(Collections.emptyList());

                WorkflowMonitor monitor = new WorkflowMonitor(mockClient, mockStateManager);
                monitor.run();

                WorkflowRun completedRun = new WorkflowRun(
                                123L, "Build", "completed", "success",
                                "main", "abc1234567890",
                                now, now.plusSeconds(60), now);

                when(mockClient.getWorkflowRun(123L)).thenReturn(completedRun);

                monitor.run();
                verify(mockClient, times(1)).getWorkflowRun(123L);

                // verify activeRunIds are cleared so getWorkflowRun should not be called again
                monitor.run();
                verify(mockClient, times(1)).getWorkflowRun(123L);
        }
}
