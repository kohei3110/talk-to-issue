package com.github.talktoissue.commands;

import com.github.talktoissue.App;
import com.github.talktoissue.server.AgentServer;
import com.github.talktoissue.server.EventRouter;
import com.github.talktoissue.server.Scheduler;
import com.github.talktoissue.server.WebhookValidator;
import com.github.talktoissue.server.WorkQueue;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

@Command(
    name = "serve",
    description = "Start autonomous agent server: receives GitHub webhooks and runs pipelines continuously."
)
public class ServeCommand implements Callable<Integer> {

    @ParentCommand
    private App parent;

    @Option(names = {"--port"}, defaultValue = "8080",
            description = "HTTP server port. Default: ${DEFAULT-VALUE}")
    private int port;

    @Option(names = {"--webhook-secret"},
            description = "GitHub webhook secret for signature validation. If omitted, signatures are not checked.")
    private String webhookSecret;

    @Option(names = {"--context-config"},
            description = "Path to YAML context configuration for scheduled polling")
    private Path contextConfig;

    @Option(names = {"--poll-interval"}, defaultValue = "0",
            description = "Polling interval in minutes. 0 = disabled. Default: ${DEFAULT-VALUE}")
    private int pollInterval;

    @Option(names = {"--trigger-label"}, defaultValue = "agent-ready",
            description = "Issue label that triggers implementation. Default: ${DEFAULT-VALUE}")
    private String triggerLabel;

    @Option(names = {"--concurrency"}, defaultValue = "2",
            description = "Max concurrent pipeline executions. Default: ${DEFAULT-VALUE}")
    private int concurrency;

    @Option(names = {"--autonomous-interval"}, defaultValue = "0",
            description = "Autonomous cycle interval in minutes. 0 = disabled. Default: ${DEFAULT-VALUE}")
    private int autonomousInterval;

    @Option(names = {"--autonomous-max-issues"}, defaultValue = "3",
            description = "Maximum issues per autonomous cycle. Default: ${DEFAULT-VALUE}")
    private int autonomousMaxIssues;

    @Option(names = {"--autonomous-min-score"}, defaultValue = "70",
            description = "Minimum quality score for autonomous implementation. Default: ${DEFAULT-VALUE}")
    private int autonomousMinScore;

    @Override
    public Integer call() throws Exception {
        String token = System.getenv("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("Error: GITHUB_TOKEN environment variable is not set.");
            return 1;
        }

        File workingDir = parent.getWorkingDir();
        if (workingDir == null || !workingDir.isDirectory()) {
            System.err.println("Error: --working-dir is required for the serve command.");
            return 1;
        }

        String model = parent.getModel();
        boolean dryRun = parent.isDryRun();
        String repoFullName = parent.getRepoFullName();

        if (dryRun) {
            System.out.println("=== DRY-RUN MODE ===");
        }

        // Build components
        var workQueue = new WorkQueue(concurrency);
        var eventRouter = new EventRouter(workQueue, repoFullName, workingDir,
            model, dryRun, triggerLabel, contextConfig);
        var webhookValidator = webhookSecret != null ? new WebhookValidator(webhookSecret) : null;
        var server = new AgentServer(eventRouter, webhookValidator, workQueue);

        // Optional scheduler
        Scheduler scheduler = null;
        if (pollInterval > 0 && contextConfig != null) {
            scheduler = new Scheduler(contextConfig, repoFullName, workingDir, model, dryRun);
        } else if (autonomousInterval > 0) {
            // Create scheduler without contextConfig for autonomous-only mode
            scheduler = new Scheduler(null, repoFullName, workingDir, model, dryRun);
        }

        // Graceful shutdown
        var shutdownLatch = new CountDownLatch(1);
        final Scheduler schedulerRef = scheduler;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[ServeCommand] Shutting down gracefully...");
            server.stop();
            if (schedulerRef != null) {
                schedulerRef.shutdown();
            }
            workQueue.shutdown(30);
            System.out.println("[ServeCommand] Shutdown complete.");
            shutdownLatch.countDown();
        }));

        // Start
        server.start(port);
        if (scheduler != null && pollInterval > 0) {
            scheduler.start(pollInterval);
        }
        if (scheduler != null && autonomousInterval > 0) {
            scheduler.startAutonomousCycle(autonomousInterval, autonomousMaxIssues, autonomousMinScore);
        }

        System.out.println("\n=== Agent Server Running ===");
        System.out.println("Repository: " + repoFullName);
        System.out.println("Trigger label: " + triggerLabel);
        System.out.println("Concurrency: " + concurrency);
        if (scheduler != null && pollInterval > 0) {
            System.out.println("Poll interval: " + pollInterval + " minutes");
        }
        if (autonomousInterval > 0) {
            System.out.println("Autonomous cycle: every " + autonomousInterval + " minutes (max-issues: "
                + autonomousMaxIssues + ", min-score: " + autonomousMinScore + ")");
        }
        System.out.println("\nTo forward webhooks locally, run:");
        System.out.println("  npx smee-client --url https://smee.io/<your-channel> --target http://localhost:" + port + "/webhooks/github");
        System.out.println("\nPress Ctrl+C to stop.\n");

        // Block until shutdown
        shutdownLatch.await();
        return 0;
    }
}
