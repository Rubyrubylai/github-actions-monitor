package dev.ruby.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowRun {
    public long id;
    public String name;
    public String status;
    public String conclusion;
    @JsonProperty("head_branch")
    public String headBranch;
    @JsonProperty("head_sha")
    public String headSha;
    @JsonProperty("created_at")
    public Instant createdAt;
    @JsonProperty("updated_at")
    public Instant updatedAt;
    @JsonProperty("run_started_at")
    public Instant runStartedAt;
}
