package dev.ruby.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowStep {
    public String name;
    public String status;
    public String conclusion;
    public int number;
    @JsonProperty("started_at")
    public Instant startedAt;
    @JsonProperty("completed_at")
    public Instant completedAt;
}
