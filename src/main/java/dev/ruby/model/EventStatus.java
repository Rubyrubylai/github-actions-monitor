package dev.ruby.model;

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
