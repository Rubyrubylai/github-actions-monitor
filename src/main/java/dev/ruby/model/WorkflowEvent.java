package dev.ruby.model;

import java.time.Instant;

public class WorkflowEvent {
  private final String key;
  private final Instant time;
  private final WorkflowLevel level;
  private final EventStatus status;
  private final String branch;
  private final String sha;
  private final String name;

  public WorkflowEvent(String id, Instant time, WorkflowLevel level, EventStatus status, String branch, String sha,
      String name) {
    this.key = String.format("%s_%s_%s_%s", id, time, level, status);
    this.time = time;
    this.level = level;
    this.status = status;
    this.branch = branch;
    this.sha = sha;
    this.name = name;
  }

  public String getKey() {
    return key;
  }

  public Instant getTime() {
    return time;
  }

  public void print() {
    System.out.printf("%-24s | %-5s | %-14s | %-10s | %-8s | %s%n", time, level, status, branch,
        (sha != null && sha.length() > 7) ? sha.substring(0, 7) : sha, name);
  }
}
