package dev.ruby.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.ruby.client.GitHubClient;
import dev.ruby.client.dto.WorkflowJob;
import dev.ruby.client.dto.WorkflowRun;
import dev.ruby.client.dto.WorkflowStep;
import dev.ruby.mapper.EventMapper;
import dev.ruby.model.EventStatus;
import dev.ruby.model.WorkflowEvent;
import dev.ruby.persistence.MonitorState;
import dev.ruby.persistence.StateStore;

public class WorkflowMonitor implements Runnable {
    private final GitHubClient client;
    private final StateStore stateStore;
    private final MonitorState state;
    private boolean isFirstRun = true;
    private final Set<Long> activeRunIds = new HashSet<>();

    public WorkflowMonitor(GitHubClient client, StateStore stateStore) {
        this.client = client;
        this.stateStore = stateStore;
        this.state = stateStore.load();
        if (this.state.getLastRunTime() == null) {
            this.state.setLastRunTime(Instant.now());
        }
    }

    @Override
    public void run() {
        try {
            List<WorkflowRun> runs = pollRuns(state.getLastRunTime());
            runs.sort(Comparator.comparing(run -> run.updatedAt()));

            if (isFirstRun) {
                printTableHeader();
                isFirstRun = false;
            }

            // avoid duplicate fetch in one round
            Set<Long> processedInFirstIteration = new HashSet<>();

            for (WorkflowRun run : runs) {
                processRun(run);

                processedInFirstIteration.add(run.id());

                if (EventMapper.toStatus(run.status(), run.conclusion()).isFinished()) {
                    activeRunIds.remove(run.id());
                } else {
                    activeRunIds.add(run.id());
                }

                if (run.updatedAt().isAfter(state.getLastRunTime())) {
                    state.setLastRunTime(run.updatedAt());
                }
            }

            // poll incomplete runs to avoid waiting for the next updatedAt sync
            var iterator = activeRunIds.iterator();
            while (iterator.hasNext()) {
                long runId = iterator.next();
                if (processedInFirstIteration.contains(runId)) {
                    continue;
                }

                WorkflowRun run = client.getWorkflowRun(runId);
                processRun(run);

                if (EventMapper.toStatus(run.status(), run.conclusion()).isFinished()) {
                    iterator.remove();
                }

                if (run.updatedAt().isAfter(state.getLastRunTime())) {
                    state.setLastRunTime(run.updatedAt());
                }
            }

            stateStore.save(state);
        } catch (Exception e) {
            System.err.println("Error processing RUN: " + e.getMessage());
        }
    }

    public MonitorState getState() {
        return state;
    }

    private void processRun(WorkflowRun run) throws Exception {
        EventStatus runStatus = EventMapper.toStatus(run.status(), run.conclusion());

        if (runStatus == EventStatus.QUEUED || runStatus == EventStatus.UNKNOWN) {
            report(EventMapper.toRunEvent(run));
            return;
        }

        report(EventMapper.toRunStartedEvent(run));

        List<WorkflowJob> jobs = client.getJobsForRun(run.id());
        for (WorkflowJob job : jobs) {
            if (job.startedAt() == null) {
                continue;
            }
            processJob(run, job);
        }

        if (runStatus.isFinished()) {
            report(EventMapper.toRunEvent(run));
        }
    }

    private void processJob(WorkflowRun run, WorkflowJob job) throws Exception {
        EventStatus jobStatus = EventMapper.toStatus(job.status(), job.conclusion());
        report(EventMapper.toJobStartedEvent(run, job));

        for (WorkflowStep step : job.steps()) {
            if (step.startedAt() == null) {
                break;
            }
            processStep(run, job, step);
        }

        if (jobStatus.isFinished()) {
            report(EventMapper.toJobEvent(run, job));
        }
    }

    private void processStep(WorkflowRun run, WorkflowJob job, WorkflowStep step) throws Exception {
        EventStatus stepStatus = EventMapper.toStatus(step.status(), step.conclusion());
        report(EventMapper.toStepStartedEvent(run, job, step));

        if (stepStatus.isFinished()) {
            report(EventMapper.toStepEvent(run, job, step));
        }
    }

    private List<WorkflowRun> pollRuns(Instant lastRunTime) throws Exception {
        int page = 1;
        int MAX_PAGES = 10;
        boolean hasMore = true;
        List<WorkflowRun> runs = new ArrayList<>();

        while (hasMore) {
            List<WorkflowRun> pageRuns = client.getWorkflowRuns(page, 100);

            if (pageRuns.isEmpty())
                break;

            boolean pageContainsNewData = false;
            for (WorkflowRun run : pageRuns) {
                if (run.updatedAt().isAfter(lastRunTime)) {
                    runs.add(run);
                    pageContainsNewData = true;
                }
            }

            if (!pageContainsNewData) {
                hasMore = false;
            } else {
                page++;
            }

            if (page > MAX_PAGES) {
                System.err.println("Warning: Too many new runs. Only the latest 1000 events are retrieved.");
                break;
            }
        }

        return runs;
    }

    private void report(WorkflowEvent event) {
        if (!state.isNewEvent(event.getKey(), event.getTime())) {
            return;
        }
        event.print();
    }

    private void printTableHeader() {
        System.out.printf("%-24s | %-5s | %-14s | %-10s | %-8s | %s%n",
                "Date Time", "Level", "Status", "Branch", "SHA", "Name");

        System.out.println("-".repeat(24) + "-+-" + "-".repeat(5) + "-+-" +
                "-".repeat(14) + "-+-" + "-".repeat(10) + "-+-" + "-".repeat(8) + "-+-" + "-".repeat(30));
    }
}
