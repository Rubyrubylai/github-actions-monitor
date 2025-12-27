package dev.ruby.client.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// @formatter:off
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowStep(
    String name,
    String status,
    String conclusion,
    int number,
    @JsonProperty("started_at") Instant startedAt,
    @JsonProperty("completed_at") Instant completedAt
) {}
