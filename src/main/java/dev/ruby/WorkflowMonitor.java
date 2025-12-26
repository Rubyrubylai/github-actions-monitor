package dev.ruby;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
                String branch = r.headBranch;
                String sha = r.headSha;
                if (WorkflowStatus.isNotStarted(r.status)) {
                    report(new Event(r.id, r.createdAt, WorkflowLevel.RUN, EventStatus.QUEUE, branch, sha, r.name));
                    continue;
                }

                Event runCompletedEvent = new Event(r.id, r.updatedAt, WorkflowLevel.RUN,
                        EventStatus.fromConclusion(r.conclusion), branch, sha, r.name);
                if (state.alreadySeenKeys.containsKey(runCompletedEvent.key)) {
                    continue;
                }
                report(new Event(r.id, r.createdAt, WorkflowLevel.RUN, EventStatus.START, branch, sha,
                        r.name));

                List<WorkflowJob> jobs = client.getJobsForRun(r.id);
                for (WorkflowJob j : jobs) {
                    if (j.startedAt == null) {
                        continue;
                    }
                    report(new Event(j.id, j.startedAt, WorkflowLevel.JOB, EventStatus.START, branch, sha, j.name));

                    for (WorkflowStep s : j.steps) {
                        if (s.startedAt == null) {
                            break;
                        }
                        report(new Event(j.id + s.number, s.startedAt, WorkflowLevel.STEP, EventStatus.START, branch,
                                sha,
                                s.name));

                        if (WorkflowStatus.isFinished(s.status)) {
                            report(new Event(j.id + s.number, s.completedAt, WorkflowLevel.STEP,
                                    EventStatus.fromConclusion(s.conclusion), branch,
                                    sha,
                                    s.name));
                        }
                    }

                    if (WorkflowStatus.isFinished(j.status)) {
                        report(new Event(j.id, j.completedAt, WorkflowLevel.JOB,
                                EventStatus.fromConclusion(j.conclusion), branch, sha, j.name));
                    }

                }

                if (WorkflowStatus.isFinished(r.status)) {
                    report(new Event(r.id, r.updatedAt, WorkflowLevel.RUN, EventStatus.fromConclusion(r.conclusion),
                            branch, sha, r.name));
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
        RUN("RUN"),
        JOB("JOB"),
        STEP("STEP");

        private final String value;

        WorkflowLevel(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private enum WorkflowStatus {
        REQUESTED("requested"),
        QUEUED("queued"),
        WAITING("waiting"),
        PENDING("pending"),
        IN_PROGRESS("in_progress"),
        COMPLETED("completed");

        private final String value;

        WorkflowStatus(String value) {
            this.value = value;
        }

        public static boolean isNotStarted(String s) {
            if (s == null)
                return false;

            try {
                WorkflowStatus status = fromValue(s);
                return status == QUEUED ||
                        status == REQUESTED ||
                        status == WAITING ||
                        status == PENDING;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        public static boolean isFinished(String s) {
            if (s == null)
                return false;

            try {
                WorkflowStatus status = fromValue(s);
                return status == COMPLETED;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        private static WorkflowStatus fromValue(String s) {
            for (WorkflowStatus st : values()) {
                if (st.value.equals(s))
                    return st;
            }
            return PENDING;
        }
    }

    private enum EventStatus {
        QUEUE("queue"),
        START("start"),
        SUCCESS("success"),
        FAILURE("failure"),
        CANCELLED("cancelled"),
        SKIPPED("skipped"),
        COMPLETED("completed");

        private final String value;

        EventStatus(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

        public static EventStatus fromConclusion(String conclusion) {
            if (conclusion == null)
                return COMPLETED;

            return switch (conclusion.toLowerCase()) {
                case "success" -> SUCCESS;
                case "failure" -> FAILURE;
                case "cancelled" -> CANCELLED;
                case "skipped" -> SKIPPED;
                default -> COMPLETED;
            };
        }
    }
}
