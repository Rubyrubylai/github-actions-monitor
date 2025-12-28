package dev.ruby.mapper;

import java.time.Instant;

import dev.ruby.client.dto.WorkflowJob;
import dev.ruby.client.dto.WorkflowRun;
import dev.ruby.client.dto.WorkflowStep;
import dev.ruby.model.EventStatus;
import dev.ruby.model.WorkflowEvent;
import dev.ruby.model.WorkflowLevel;

public class EventMapper {
  public static WorkflowEvent toRunEvent(WorkflowRun run) {
    EventStatus status = toStatus(run.status(), run.conclusion());
    Instant timestamp = status.isFinished() ? run.updatedAt() : run.createdAt();

    return new WorkflowEvent(String.valueOf(run.id()), timestamp, WorkflowLevel.RUN, status, run.headBranch(),
        run.headSha(), run.name());
  }

  public static WorkflowEvent toRunStartedEvent(WorkflowRun run) {
    return new WorkflowEvent(String.valueOf(run.id()), run.createdAt(), WorkflowLevel.RUN, EventStatus.STARTED,
        run.headBranch(), run.headSha(), run.name());
  }

  public static WorkflowEvent toJobEvent(WorkflowRun run, WorkflowJob job) {
    EventStatus status = toStatus(job.status(), job.conclusion());
    Instant timestamp = status.isFinished() ? job.completedAt() : job.startedAt();

    return new WorkflowEvent(String.valueOf(job.id()), timestamp, WorkflowLevel.JOB, status, run.headBranch(),
        run.headSha(), job.name());
  }

  public static WorkflowEvent toJobStartedEvent(WorkflowRun run, WorkflowJob job) {
    return new WorkflowEvent(String.valueOf(job.id()), job.startedAt(), WorkflowLevel.JOB, EventStatus.STARTED,
        run.headBranch(), run.headSha(), job.name());
  }

  public static WorkflowEvent toStepEvent(WorkflowRun run, WorkflowJob job, WorkflowStep step) {
    EventStatus status = toStatus(step.status(), step.conclusion());
    Instant timestamp = status.isFinished() ? step.completedAt() : step.startedAt();

    return new WorkflowEvent(job.id() + ":" + step.number(), timestamp, WorkflowLevel.STEP, status, run.headBranch(),
        run.headSha(), step.name());
  }

  public static WorkflowEvent toStepStartedEvent(WorkflowRun run, WorkflowJob job, WorkflowStep step) {
    return new WorkflowEvent(job.id() + ":" + step.number(), step.startedAt(), WorkflowLevel.STEP, EventStatus.STARTED,
        run.headBranch(), run.headSha(), step.name());
  }

  public static EventStatus toStatus(String status, String conclusion) {
    if (status == null)
      return EventStatus.UNKNOWN;

    return switch (status.toLowerCase()) {
      case "queued", "requested", "waiting", "pending" -> EventStatus.QUEUED;
      case "in_progress" -> EventStatus.STARTED;
      case "completed" -> fromConclusion(conclusion);
      default -> EventStatus.UNKNOWN;
    };
  }

  private static EventStatus fromConclusion(String conclusion) {
    if (conclusion == null)
      return EventStatus.SUCCESS;

    return switch (conclusion.toLowerCase()) {
      case "success" -> EventStatus.SUCCESS;
      case "failure", "timed_out", "action_required", "stale" -> EventStatus.FAILURE;
      case "cancelled" -> EventStatus.CANCELLED;
      case "skipped" -> EventStatus.SKIPPED;
      default -> EventStatus.SUCCESS;
    };
  }
}
