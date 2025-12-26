package dev.ruby;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GitHubClient {
    private final String owner;
    private final String repo;
    private final String token;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GitHubClient(String owner, String repo, String token) {
        this.owner = owner;
        this.repo = repo;
        this.token = token;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    public List<WorkflowRun> getWorkflowRuns(int page, int perPage) throws Exception {
        String url = String.format("https://api.github.com/repos/%s/%s/actions/runs?page=%d&per_page=%d",
                owner, repo, page, perPage);

        HttpRequest request = buildRequest(url);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API Error: " + response.statusCode() + " " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        WorkflowRun[] runs = objectMapper.treeToValue(root.path("workflow_runs"), WorkflowRun[].class);
        return Arrays.asList(runs);
    }

    public List<Job> getJobsForRun(long runId) throws Exception {
        String url = String.format("https://api.github.com/repos/%s/%s/actions/runs/%d/jobs", owner, repo, runId);

        HttpRequest request = buildRequest(url);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API Error: " + response.statusCode() + " " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        Job[] jobs = objectMapper.treeToValue(root.path("jobs"), Job[].class);
        return Arrays.asList(jobs);
    }

    private HttpRequest buildRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class WorkflowRun {
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

@JsonIgnoreProperties(ignoreUnknown = true)
class Job {
    public long id;
    public String name;
    public String status;
    public String conclusion;
    @JsonProperty("started_at")
    public Instant startedAt;
    @JsonProperty("completed_at")
    public Instant completedAt;
    public List<Step> steps = new ArrayList<>();
}

@JsonIgnoreProperties(ignoreUnknown = true)
class Step {
    public String name;
    public String status;
    public String conclusion;
    public int number;
    @JsonProperty("started_at")
    public Instant startedAt;
    @JsonProperty("completed_at")
    public Instant completedAt;
}
