package dev.ruby;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        String owner = System.getProperty("owner");
        String repo = System.getProperty("repo");
        String token = System.getProperty("token");

        if (owner == null || repo == null || token == null) {
            System.err.println("Error: Missing required properties -Downer, -Drepo, or -Dtoken");
            System.exit(1);
        }

        GitHubClient client = new GitHubClient(owner, repo, token);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        StateManager stateManager = new StateManager();
        WorkflowMonitor monitor = new WorkflowMonitor(client, stateManager);
        scheduler.scheduleAtFixedRate(monitor, 0, 10, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            scheduler.shutdown();

            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                stateManager.save(monitor.getState());
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }));
    }
}
