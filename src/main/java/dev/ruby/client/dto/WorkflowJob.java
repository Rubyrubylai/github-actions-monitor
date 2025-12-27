package dev.ruby.client.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// @formatter:off
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowJob(
    long id,
    String name,
    String status,
    String conclusion,
    @JsonProperty("started_at") Instant startedAt,
    @JsonProperty("completed_at") Instant completedAt,
    List<WorkflowStep> steps
) {
    public WorkflowJob {
        if (steps == null) {
            steps = new ArrayList<>();
        }
    }
}
