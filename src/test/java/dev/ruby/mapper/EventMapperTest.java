package dev.ruby.mapper;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import dev.ruby.client.dto.WorkflowJob;
import dev.ruby.client.dto.WorkflowRun;
import dev.ruby.client.dto.WorkflowStep;
import dev.ruby.model.EventStatus;
import dev.ruby.model.WorkflowEvent;
import dev.ruby.model.WorkflowLevel;

class EventMapperTest {

  @Test
  void toRunEvent_finishedRunUsesUpdatedTimeAndSuccessStatus() {
    Instant createdAt = Instant.now();
    Instant updatedAt = createdAt.plusSeconds(300);
    WorkflowRun run = run("completed", "success", createdAt, updatedAt);

    WorkflowEvent event = EventMapper.toRunEvent(run);

    assertEquals(updatedAt, event.getTime());
    assertEquals(key("1", updatedAt, WorkflowLevel.RUN, EventStatus.SUCCESS), event.getKey());
  }

  @Test
  void toRunEvent_inProgressRunUsesCreatedTimeAndStartedStatus() {
    Instant createdAt = Instant.now();
    Instant updatedAt = createdAt.plusSeconds(120);
    WorkflowRun run = run("in_progress", null, createdAt, updatedAt);

    WorkflowEvent event = EventMapper.toRunEvent(run);

    assertEquals(createdAt, event.getTime());
    assertEquals(key("1", createdAt, WorkflowLevel.RUN, EventStatus.STARTED), event.getKey());
  }

  @Test
  void toRunStartedEvent_alwaysBuildsStartedStatusAtCreatedTime() {
    Instant createdAt = Instant.now();
    WorkflowRun run = run("completed", "failure", createdAt, createdAt.plusSeconds(120));

    WorkflowEvent event = EventMapper.toRunStartedEvent(run);

    assertEquals(createdAt, event.getTime());
    assertEquals(key("1", createdAt, WorkflowLevel.RUN, EventStatus.STARTED), event.getKey());
  }

  @Test
  void toJobEvent_finishedRunUsesJobCompletedTimeAndFailureStatus() {
    Instant createdAt = Instant.now();
    Instant updatedAt = createdAt.plusSeconds(300);
    Instant jobStarted = createdAt.plusSeconds(60);
    Instant jobCompleted = jobStarted.plusSeconds(180);
    WorkflowRun run = run("completed", "failure", createdAt, updatedAt);
    WorkflowJob job = job("completed", "failure", jobStarted, jobCompleted);

    WorkflowEvent event = EventMapper.toJobEvent(run, job);

    assertEquals(jobCompleted, event.getTime());
    assertEquals(key("10", jobCompleted, WorkflowLevel.JOB, EventStatus.FAILURE), event.getKey());
  }

  @Test
  void toJobEvent_inProgressRunUsesJobStartedTime() {
    Instant createdAt = Instant.now();
    Instant jobStarted = createdAt.plusSeconds(60);
    WorkflowRun run = run("in_progress", null, createdAt, createdAt.plusSeconds(120));
    WorkflowJob job = job("in_progress", null, jobStarted, jobStarted.plusSeconds(180));

    WorkflowEvent event = EventMapper.toJobEvent(run, job);

    assertEquals(jobStarted, event.getTime());
    assertEquals(key("10", jobStarted, WorkflowLevel.JOB, EventStatus.STARTED), event.getKey());
  }

  @Test
  void toJobStartedEvent_alwaysBuildsStartedStatusAtJobStart() {
    Instant createdAt = Instant.now();
    Instant jobStarted = createdAt.plusSeconds(60);
    WorkflowRun run = run("in_progress", null, createdAt, createdAt.plusSeconds(120));
    WorkflowJob job = job("completed", "failure", jobStarted, jobStarted.plusSeconds(180));

    WorkflowEvent event = EventMapper.toJobStartedEvent(run, job);

    assertEquals(jobStarted, event.getTime());
    assertEquals(key("10", jobStarted, WorkflowLevel.JOB, EventStatus.STARTED), event.getKey());
  }

  @Test
  void toStepEvent_finishedRunUsesStepCompletedTimeAndCancelledStatus() {
    Instant createdAt = Instant.now();
    Instant jobStarted = createdAt.plusSeconds(60);
    Instant jobCompleted = jobStarted.plusSeconds(180);
    Instant stepStarted = jobStarted.plusSeconds(60);
    Instant stepCompleted = stepStarted.plusSeconds(30);
    WorkflowRun run = run("completed", "cancelled", createdAt, createdAt.plusSeconds(300));
    WorkflowJob job = job("completed", "cancelled", jobStarted, jobCompleted);
    WorkflowStep step = step(1, "completed", "cancelled", stepStarted, stepCompleted);

    WorkflowEvent event = EventMapper.toStepEvent(run, job, step);

    assertEquals(stepCompleted, event.getTime());
    assertEquals(key("10:1", stepCompleted, WorkflowLevel.STEP, EventStatus.CANCELLED), event.getKey());
  }

  @Test
  void toStepEvent_inProgressRunUsesStepStartedTime() {
    Instant createdAt = Instant.now();
    Instant jobStarted = createdAt.plusSeconds(60);
    Instant stepStarted = jobStarted.plusSeconds(60);
    WorkflowRun run = run("in_progress", null, createdAt, createdAt.plusSeconds(120));
    WorkflowJob job = job("in_progress", null, jobStarted, jobStarted.plusSeconds(180));
    WorkflowStep step = step(1, "in_progress", null, stepStarted, stepStarted.plusSeconds(30));

    WorkflowEvent event = EventMapper.toStepEvent(run, job, step);

    assertEquals(stepStarted, event.getTime());
    assertEquals(key("10:1", stepStarted, WorkflowLevel.STEP, EventStatus.STARTED), event.getKey());
  }

  @Test
  void toStepStartedEvent_alwaysBuildsStartedStatusAtStepStart() {
    Instant createdAt = Instant.now();
    Instant jobStarted = createdAt.plusSeconds(60);
    Instant stepStarted = jobStarted.plusSeconds(60);
    WorkflowRun run = run("in_progress", null, createdAt, createdAt.plusSeconds(120));
    WorkflowJob job = job("in_progress", null, jobStarted, jobStarted.plusSeconds(180));
    WorkflowStep step = step(1, "completed", "failure", stepStarted, stepStarted.plusSeconds(30));

    WorkflowEvent event = EventMapper.toStepStartedEvent(run, job, step);

    assertEquals(stepStarted, event.getTime());
    assertEquals(key("10:1", stepStarted, WorkflowLevel.STEP, EventStatus.STARTED), event.getKey());
  }

  @Test
  void toStatus_handlesNullStatusAsUnknown() {
    assertEquals(EventStatus.UNKNOWN, EventMapper.toStatus(null, null));
  }

  @Test
  void toStatus_completedNullConclusionDefaultsToSuccess() {
    assertEquals(EventStatus.SUCCESS, EventMapper.toStatus("completed", null));
  }

  private String key(String id, Instant time, WorkflowLevel level,
      EventStatus status) {
    return String.format("%s_%s_%s_%s", id, time, level, status);
  }

  private WorkflowRun run(String status, String conclusion, Instant createdAt,
      Instant updatedAt) {
    return new WorkflowRun(1L, "Build", status, conclusion, "main",
        "abcdef123456", createdAt, updatedAt,
        createdAt);
  }

  private WorkflowJob job(String status, String conclusion, Instant startedAt, Instant completedAt) {
    return new WorkflowJob(10L, "Job", status, conclusion, startedAt,
        completedAt, List.of());
  }

  private WorkflowStep step(int number, String status, String conclusion, Instant startedAt, Instant completedAt) {
    return new WorkflowStep("Step", status, conclusion, number, startedAt,
        completedAt);
  }
}
