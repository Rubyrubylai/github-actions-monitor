package dev.ruby;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import dev.ruby.client.GitHubClient;
import dev.ruby.persistence.StateStore;
import dev.ruby.service.WorkflowMonitor;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar monitor.jar <owner/repo> <personal_access_token>");
            System.exit(1);
        }

        String fullRepo = args[0];
        String token = args[1];

        String[] parts = fullRepo.split("/");
        if (parts.length != 2) {
            System.err.println("Invalid repository format. Use 'owner/repo'.");
            System.exit(1);
        }

        String owner = parts[0];
        String repo = parts[1];

        GitHubClient client = new GitHubClient(owner, repo, token);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        StateStore stateManager = new StateStore(owner + "-" + repo);
        WorkflowMonitor monitor = new WorkflowMonitor(client, stateManager);
        scheduler.scheduleWithFixedDelay(monitor, 0, 10, TimeUnit.SECONDS);

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
