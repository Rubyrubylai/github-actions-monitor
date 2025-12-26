package dev.ruby;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
                processRun(r);
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
            report(new Event(r.id, r.createdAt, WorkflowLevel.RUN, runStatus, branch, sha, r.name));
            return;
        }

        report(new Event(r.id, r.createdAt, WorkflowLevel.RUN, runStatus, branch, sha,
                r.name));

        List<WorkflowJob> jobs = client.getJobsForRun(r.id);
        for (WorkflowJob j : jobs) {
            if (j.startedAt == null) {
                continue;
            }
            EventStatus jobStatus = EventStatus.map(j.status, j.conclusion);

            report(new Event(j.id, j.startedAt, WorkflowLevel.JOB, runStatus, branch, sha, j.name));

            for (WorkflowStep s : j.steps) {
                if (s.startedAt == null) {
                    break;
                }
                EventStatus stepStatus = EventStatus.map(s.status, s.conclusion);

                report(new Event(j.id + s.number, s.startedAt, WorkflowLevel.STEP, runStatus, branch, sha,
                        s.name));

                if (stepStatus.isFinished()) {
                    report(new Event(j.id + s.number, s.completedAt, WorkflowLevel.STEP, stepStatus, branch,
                            sha, s.name));
                }
            }

            if (jobStatus.isFinished()) {
                report(new Event(j.id, j.completedAt, WorkflowLevel.JOB, jobStatus, branch, sha, j.name));
            }

        }

        if (runStatus.isFinished()) {
            report(new Event(r.id, r.updatedAt, WorkflowLevel.RUN, runStatus, branch, sha, r.name));
        }

        if (r.updatedAt.isAfter(state.lastRunTime)) {
            state.lastRunTime = r.updatedAt;
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

        Event(long id, Instant time, WorkflowLevel level, EventStatus status, String branch, String sha, String name) {
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
