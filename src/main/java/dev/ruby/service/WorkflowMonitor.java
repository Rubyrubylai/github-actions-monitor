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
import dev.ruby.model.EventStatus;
import dev.ruby.model.RunContext;
import dev.ruby.model.WorkflowEvent;
import dev.ruby.model.WorkflowLevel;
import dev.ruby.persistence.MonitorState;
import dev.ruby.persistence.StateManager;

public class WorkflowMonitor implements Runnable {
    private final GitHubClient client;
    private final StateManager stateManager;
    private final MonitorState state;
    private boolean isFirstRun = true;
    private final Set<Long> activeRunIds = new HashSet<>();

    public WorkflowMonitor(GitHubClient client, StateManager stateManager) {
        this.client = client;
        this.stateManager = stateManager;
        this.state = stateManager.load();
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

            for (WorkflowRun r : runs) {
                processRun(r);

                processedInFirstIteration.add(r.id());

                if (!EventStatus.map(r.status(), r.conclusion()).isFinished()) {
                    this.activeRunIds.add(r.id());
                } else {
                    activeRunIds.remove(r.id());
                }

                if (r.updatedAt().isAfter(state.getLastRunTime())) {
                    state.setLastRunTime(r.updatedAt());
                }
            }

            // poll incomplete runs to avoid waiting for the next updatedAt sync
            var iterator = activeRunIds.iterator();
            while (iterator.hasNext()) {
                long rId = iterator.next();
                if (processedInFirstIteration.contains(rId)) {
                    continue;
                }

                WorkflowRun r = client.getWorkflowRun(rId);
                processRun(r);

                if (EventStatus.map(r.status(), r.conclusion()).isFinished()) {
                    iterator.remove();
                }

                if (r.updatedAt().isAfter(state.getLastRunTime())) {
                    state.setLastRunTime(r.updatedAt());
                }
            }

            stateManager.save(state);
        } catch (Exception e) {
            System.err.println("Error polling API: " + e.getMessage());
        }
    }

    public MonitorState getState() {
        return this.state;
    }

    private void processRun(WorkflowRun r) throws Exception {
        EventStatus runStatus = EventStatus.map(r.status(), r.conclusion());

        RunContext runCtx = new RunContext(r.headBranch(), r.headSha());
        if (runStatus == EventStatus.QUEUED || runStatus == EventStatus.UNKNOWN) {
            report(new WorkflowEvent(String.valueOf(r.id()), r.createdAt(), WorkflowLevel.RUN, runStatus, runCtx,
                    r.name()));
            return;
        }

        report(new WorkflowEvent(String.valueOf(r.id()), r.createdAt(), WorkflowLevel.RUN, EventStatus.STARTED, runCtx,
                r.name()));

        List<WorkflowJob> jobs = client.getJobsForRun(r.id());
        for (WorkflowJob j : jobs) {
            if (j.startedAt() == null) {
                continue;
            }
            processJob(j, runCtx);
        }

        if (runStatus.isFinished()) {
            report(new WorkflowEvent(String.valueOf(r.id()), r.updatedAt(), WorkflowLevel.RUN, runStatus, runCtx,
                    r.name()));
        }
    }

    private void processJob(WorkflowJob j, RunContext runCtx) throws Exception {
        EventStatus jobStatus = EventStatus.map(j.status(), j.conclusion());
        report(new WorkflowEvent(String.valueOf(j.id()), j.startedAt(), WorkflowLevel.JOB, EventStatus.STARTED, runCtx,
                j.name()));

        for (WorkflowStep s : j.steps()) {
            if (s.startedAt() == null) {
                break;
            }
            processStep(s, j.id(), runCtx);
        }

        if (jobStatus.isFinished()) {
            report(new WorkflowEvent(String.valueOf(j.id()), j.completedAt(), WorkflowLevel.JOB, jobStatus, runCtx,
                    j.name()));
        }
    }

    private void processStep(WorkflowStep s, long jobId, RunContext runCtx) throws Exception {
        EventStatus stepStatus = EventStatus.map(s.status(), s.conclusion());
        report(new WorkflowEvent(jobId + ":" + s.number(), s.startedAt(), WorkflowLevel.STEP, EventStatus.STARTED,
                runCtx,
                s.name()));

        if (stepStatus.isFinished()) {
            report(new WorkflowEvent(jobId + ":" + s.number(), s.completedAt(), WorkflowLevel.STEP, stepStatus, runCtx,
                    s.name()));
        }
    }

    private List<WorkflowRun> pollRuns(Instant lastRunTime) throws Exception {
        int page = 1;
        int MAX_PAGES = 10;
        boolean hasMore = true;
        List<WorkflowRun> runs = new ArrayList<>();

        while (hasMore) {
            List<WorkflowRun> pageRuns = this.client.getWorkflowRuns(page, 100);

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
