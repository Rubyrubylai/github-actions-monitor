package dev.ruby.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowJob {
    public long id;
    public String name;
    public String status;
    public String conclusion;
    @JsonProperty("started_at")
    public Instant startedAt;
    @JsonProperty("completed_at")
    public Instant completedAt;
    public List<WorkflowStep> steps = new ArrayList<>();
}
