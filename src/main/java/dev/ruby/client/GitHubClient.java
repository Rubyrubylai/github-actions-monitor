package dev.ruby.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;

import dev.ruby.model.WorkflowJob;
import dev.ruby.model.WorkflowRun;

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
        HttpResponse<String> response = sendWithRetry(request);

        if (response.statusCode() != 200) {
            throw new RuntimeException("API Error: " + response.statusCode() + " " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        WorkflowRun[] runs = objectMapper.treeToValue(root.path("workflow_runs"), WorkflowRun[].class);
        return Arrays.asList(runs);
    }

    public List<WorkflowJob> getJobsForRun(long runId) throws Exception {
        String url = String.format("https://api.github.com/repos/%s/%s/actions/runs/%d/jobs", owner, repo, runId);

        HttpRequest request = buildRequest(url);
        HttpResponse<String> response = sendWithRetry(request);

        if (response.statusCode() != 200) {
            throw new RuntimeException("API Error: " + response.statusCode() + " " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        WorkflowJob[] jobs = objectMapper.treeToValue(root.path("jobs"), WorkflowJob[].class);
        return Arrays.asList(jobs);
    }

    public WorkflowRun getWorkflowRun(long runId) throws Exception {
        String url = String.format("https://api.github.com/repos/%s/%s/actions/runs/%d", owner, repo, runId);

        HttpRequest request = buildRequest(url);
        HttpResponse<String> response = sendWithRetry(request);

        if (response.statusCode() != 200) {
            throw new RuntimeException("API Error: " + response.statusCode() + " " + response.body());
        }

        WorkflowRun run = objectMapper.readValue(response.body(), WorkflowRun.class);
        return run;
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 403 || response.statusCode() == 429) {
            String remaining = response.headers().firstValue("x-ratelimit-remaining").orElse("1");
            if ("0".equals(remaining)) {
                long resetTime = Long.parseLong(response.headers().firstValue("x-ratelimit-reset").orElse("0"));
                long sleepMillis = (resetTime * 1000) - System.currentTimeMillis() + 5 * 1000;

                if (sleepMillis > 0) {
                    System.err.printf("Rate limit exceeded. Sleep for %d seconds...%n", sleepMillis / 1000);

                    try {
                        Thread.sleep(sleepMillis);
                        return sendWithRetry(request);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return response;
                    }
                }
            }
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("API Error: " + response.statusCode() + " " + response.body());
        }
        return response;
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
