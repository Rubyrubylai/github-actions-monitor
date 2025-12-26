package dev.ruby.model;

import java.time.Instant;

public class WorkflowEvent {
  private final String key;
  private final Instant time;
  private final WorkflowLevel level;
  private final EventStatus status;
  private final RunContext runCtx;
  private final String name;

  public WorkflowEvent(String id, Instant time, WorkflowLevel level, EventStatus status, RunContext runCtx,
      String name) {
    this.key = String.format("%s_%s_%s_%s", id, time, level, status);
    this.time = time;
    this.level = level;
    this.status = status;
    this.runCtx = runCtx;
    this.name = name;
  }

  public String getKey() {
    return key;
  }

  public Instant getTime() {
    return time;
  }

  public void print() {
    System.out.printf("%-24s | %-5s | %-14s | %-10s | %-8s | %s%n",
        time, level, status, runCtx.branch(), runCtx.shortSha(), name);
  }
}
