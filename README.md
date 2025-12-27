# Getting Started

## Prerequisites

- **Java**: OpenJDK 21 (GraalVM 21.0.0 or later recommended).
- **Build Tool**: Apache Maven 3.9.x.
- **GitHub Token**: A Personal Access Token (PAT) with `repo` or `workflow` scope.

## Installation & Execution

### 1. Clone the repository:

```
git clone https://github.com/Rubyrubylai/github-actions-monitor.git
cd github-actions-monitor
```

### 2. Build the project:

```
mvn clean package
```

### 3. Run the application:

You can run the monitor by providing the repository and your GitHub Token as arguments.

```
java -jar target/github-actions-monitor-1.0-SNAPSHOT.jar <owner>/<repo> <personal_access_token>
```

Note: Ensure your token has repo and workflow scopes.

## Testing

To run the tests, use the following command:

```
mvn test
```

This will execute all unit tests and display the results in the console.

# Design Decisions

## 1. Polling Strategy

- **Decision**: Implemented a REST API Polling mechanism instead of Webhooks.
- **Reasoning**: Webhooks primarily notify state changes at the workflow-run level and do not consistently provide job- and step-level details required by this tool. Even with webhooks, the implementation would still need follow-up REST API calls to fetch jobs and steps. Therefore, this project uses polling to retrieve the full execution details while keeping the tool standalone and easy to run without any server or public endpoint.
- **Reference**:
  - [List jobs for a workflow run](https://docs.github.com/en/rest/actions/workflow-jobs?apiVersion=2022-11-28#list-jobs-for-a-workflow-run)
  - [List workflow runs for a repository](https://docs.github.com/en/rest/actions/workflow-runs?apiVersion=2022-11-28#list-workflow-runs-for-a-repository)

## 2. Pagination & Early Exit Strategy

- **Approach**: Fetch workflow runs page by page and stop when updatedAt < lastRunTime for the remaining results.
- **Trade-off**: Since the API is sorted by createdAt descending, re-running a very old workflow might be missed if it falls outside the initial pages. This is an intentional trade-off to prioritize performance and simplicity, as re-running old historical workflows is a rare edge case.

## 3. Resource Protection & Rate Limiting

- **Decision**: Implemented a strict boundary by setting per_page=100 and a maximum page limit of 10.
- **Reasoning**: To prevent the application from hitting GitHub's API Rate Limits or consuming excessive memory/CPU on extremely large repositories. A 1,000-run buffer (10 pages × 100 runs) is more than sufficient to capture recent activities for the vast majority of active projects, ensuring high reliability without overwhelming the network.

## 4. Active Run Tracking

- **Decision**: Implemented an Active Run List. The system stores all non-terminal runs (e.g., in_progress, queued) in memory and performs targeted polling for these specific IDs until they reach a completed state.
- **Reasoning**: Since the updatedAt field of List Workflow Runs API may not reflect real-time updates of individual internal Jobs or Steps. Thus, polling from the specific Workflow Run API to track active runs ensures that users receive timely notifications about state changes.
- **References**:
  - [Get a workflow run](https://docs.github.com/en/rest/actions/workflow-runs?apiVersion=2022-11-28#get-a-workflow-run)

## 5. Event Deduplication

- **Decision**: Implemented a local state map to record unique keys for every notified event.
- **Reasoning**: Relying solely on the lastRunTime timestamp for filtering can lead to missing events if multiple runs are updated within the same polling interval (e.g., at the exact same second).

## 6. State Management & Persistence

- **Decision**: Used a local file-based persistence mechanism to store the lastRunTime and notified event keys.
- **Reasoning**: This approach ensures that the monitor can resume from its last known state after restarts, providing continuity in monitoring without requiring complex database setups.

## 7. State Cleanup

- **Decision**: Implemented periodic cleanup of the local state file to remove entries older than 7 days.
- **Reasoning**: This prevents the state file from growing indefinitely, ensuring efficient storage usage and maintaining optimal performance over time.

## 8. Rate Limiting Handling

- **Decision**: When a rate limit is encountered, the monitor extracts the x-ratelimit-reset header to determine the exact wait time.
- **Reasoning**: Instead of crashing or failing silently, the tool enters a "sleep" state and automatically resumes once the quota is replenished. This ensures the monitor can run unattended for long periods without manual intervention.
- **Reference**:
  - [Rate limits for the REST API](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api?apiVersion=2022-11-28)
