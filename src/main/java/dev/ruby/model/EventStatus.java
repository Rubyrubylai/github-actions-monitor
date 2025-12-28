package dev.ruby.model;

public enum EventStatus {
  QUEUED, STARTED, SUCCESS, FAILURE, CANCELLED, SKIPPED, UNKNOWN;

  public boolean isFinished() {
    return this == SUCCESS || this == FAILURE || this == CANCELLED || this == SKIPPED;
  }
}
