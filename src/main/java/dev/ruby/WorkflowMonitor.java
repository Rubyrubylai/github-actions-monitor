package dev.ruby;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import dev.ruby.WorkflowMonitor.EventStatus;
import dev.ruby.client.GitHubClient;
import dev.ruby.model.MonitorState;
import dev.ruby.model.WorkflowJob;
import dev.ruby.model.WorkflowRun;
import dev.ruby.model.WorkflowStep;

public class WorkflowMonitor implements Runnable {
    private final GitHubClient client;
    private final StateManager stateManager;
    private final MonitorState state;
    private boolean isFirstRun = true;
    private final HashSet<Long> activeRunIds = new HashSet<>();

    WorkflowMonitor(GitHubClient client, StateManager stateManager) {
        this.client = client;
        this.stateManager = stateManager;
        this.state = stateManager.load();
        if (this.state.lastRunTime == null) {
            this.state.lastRunTime = Instant.now();
        }
    }

    @Override
    public void run() {
        try {
            List<WorkflowRun> runs = pollRuns(state.lastRunTime);
            runs.sort(Comparator.comparing(run -> run.updatedAt));

            if (isFirstRun) {
                printTableHeader();
                isFirstRun = false;
            }

            for (WorkflowRun r : runs) {
                if (!this.activeRunIds.contains(r.id)) {
                    this.activeRunIds.add(r.id);
                }
            }

            // poll incomplete runs to avoid waiting for the next updatedAt sync
            var iterator = activeRunIds.iterator();
            while (iterator.hasNext()) {
                long rId = iterator.next();
                WorkflowRun r = client.getWorkflowRun(rId);
                processRun(r);

                if (EventStatus.map(r.status, r.conclusion).isFinished()) {
                    iterator.remove();
                }

                if (r.updatedAt.isAfter(state.lastRunTime)) {
                    state.lastRunTime = r.updatedAt;
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
        EventStatus runStatus = EventStatus.map(r.status, r.conclusion);

        String branch = r.headBranch;
        String sha = r.headSha;
        if (runStatus == EventStatus.QUEUED || runStatus == EventStatus.UNKNOWN) {
            report(new Event(String.valueOf(r.id), r.createdAt, WorkflowLevel.RUN, runStatus, branch, sha, r.name));
            return;
        }

        report(new Event(String.valueOf(r.id), r.createdAt, WorkflowLevel.RUN, EventStatus.STARTED, branch, sha,
                r.name));

        List<WorkflowJob> jobs = client.getJobsForRun(r.id);
        for (WorkflowJob j : jobs) {
            if (j.startedAt == null) {
                continue;
            }
            processJob(j, branch, sha);
        }

        if (runStatus.isFinished()) {
            report(new Event(String.valueOf(r.id), r.updatedAt, WorkflowLevel.RUN, runStatus, branch, sha, r.name));
        }
    }

    private void processJob(WorkflowJob j, String branch, String sha) throws Exception {
        EventStatus jobStatus = EventStatus.map(j.status, j.conclusion);
        report(new Event(String.valueOf(j.id), j.startedAt, WorkflowLevel.JOB, EventStatus.STARTED, branch, sha,
                j.name));

        for (WorkflowStep s : j.steps) {
            if (s.startedAt == null) {
                break;
            }
            processStep(s, j.id, branch, sha);
        }

        if (jobStatus.isFinished()) {
            report(new Event(String.valueOf(j.id), j.completedAt, WorkflowLevel.JOB, jobStatus, branch, sha,
                    j.name));
        }
    }

    private void processStep(WorkflowStep s, long jobId, String branch, String sha) throws Exception {
        EventStatus stepStatus = EventStatus.map(s.status, s.conclusion);
        report(new Event(jobId + "_" + s.number, s.startedAt, WorkflowLevel.STEP, EventStatus.STARTED, branch,
                sha,
                s.name));

        if (stepStatus.isFinished()) {
            report(new Event(jobId + "_" + s.number, s.completedAt, WorkflowLevel.STEP, stepStatus, branch,
                    sha, s.name));
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
                if (run.updatedAt.isAfter(lastRunTime)) {
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
            }
        }

        return runs;
    }

    private void report(Event event) {
        if (state.alreadySeenKeys.containsKey(event.key)) {
            return;
        }

        state.alreadySeenKeys.put(event.key, event.time);
        event.print();
    }

    private static class Event {
        String key;
        Instant time;
        WorkflowLevel level;
        EventStatus status;
        String branch;
        String sha;
        String name;

        Event(String id, Instant time, WorkflowLevel level, EventStatus status, String branch, String sha,
                String name) {
            this.key = String.format("%s_%s_%s_%s", id, time, level, status);
            this.time = time;
            this.level = level;
            this.status = status;
            this.branch = branch;
            this.sha = sha.substring(0, 7);
            this.name = name;
        }

        public void print() {
            System.out.printf("%-24s | %-5s | %-14s | %-10s | %-8s | %s%n", time, level, status, branch, sha, name);
        }
    }

    private void printTableHeader() {
        System.out.printf("%-24s | %-5s | %-14s | %-10s | %-8s | %s%n",
                "Date Time", "Level", "Status", "Branch", "SHA", "Name");

        System.out.println("-".repeat(24) + "-+-" + "-".repeat(5) + "-+-" +
                "-".repeat(14) + "-+-" + "-".repeat(10) + "-+-" + "-".repeat(8) + "-+-" + "-".repeat(30));
    }

    private enum WorkflowLevel {
        RUN, JOB, STEP;
    }

    public enum EventStatus {
        QUEUED, STARTED, SUCCESS, FAILURE, CANCELLED, SKIPPED, UNKNOWN;

        public static EventStatus map(String status, String conclusion) {
            if (status == null)
                return UNKNOWN;

            return switch (status.toLowerCase()) {
                case "queued", "requested", "waiting", "pending" -> QUEUED;
                case "in_progress" -> STARTED;
                case "completed" -> fromConclusion(conclusion);
                default -> UNKNOWN;
            };
        }

        private static EventStatus fromConclusion(String conclusion) {
            if (conclusion == null)
                return SUCCESS;

            return switch (conclusion.toLowerCase()) {
                case "success" -> SUCCESS;
                case "failure", "timed_out", "action_required", "stale" -> FAILURE;
                case "cancelled" -> CANCELLED;
                case "skipped" -> SKIPPED;
                default -> SUCCESS;
            };
        }

        public boolean isFinished() {
            return this == SUCCESS || this == FAILURE || this == CANCELLED || this == SKIPPED;
        }
    }
}
