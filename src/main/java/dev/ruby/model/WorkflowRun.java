package dev.ruby.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// @formatter:off
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowRun(
    long id,
    String name,
    String status,
    String conclusion,
    @JsonProperty("head_branch") String headBranch,
    @JsonProperty("head_sha") String headSha,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("run_started_at") Instant runStartedAt
) {}
