package com.github.talktoissue.server;

import com.github.copilot.sdk.CopilotClient;
import com.github.talktoissue.context.ContextAggregator;
import com.github.talktoissue.context.ContextConfig;
import com.github.talktoissue.IssueCompilerSession;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Periodic scheduler that polls context sources and runs the compile pipeline
 * at configured intervals. Tracks processed items via a local state file
 * to avoid duplicate processing.
 */
public class Scheduler {

    private final ScheduledExecutorService scheduler;
    private final Path contextConfig;
    private final String repoFullName;
    private final File workingDir;
    private final String model;
    private final boolean dryRun;

    public Scheduler(Path contextConfig, String repoFullName, File workingDir,
                     String model, boolean dryRun) {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "scheduler");
            t.setDaemon(true);
            return t;
        });
        this.contextConfig = contextConfig;
        this.repoFullName = repoFullName;
        this.workingDir = workingDir;
        this.model = model;
        this.dryRun = dryRun;
    }

    /**
     * Start the scheduler with the given poll interval.
     *
     * @param intervalMinutes how often to poll, in minutes
     */
    public void start(int intervalMinutes) {
        System.out.println("[Scheduler] Starting with " + intervalMinutes + " minute interval");
        scheduler.scheduleAtFixedRate(this::poll, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
    }

    private void poll() {
        System.out.println("[Scheduler] Polling context sources...");
        try {
            String token = System.getenv("GITHUB_TOKEN");
            GitHub gitHub = new GitHubBuilder().withOAuthToken(token).build();
            GHRepository repo = gitHub.getRepository(repoFullName);

            try (var client = new CopilotClient()) {
                client.start().get();

                var sources = ContextConfig.load(contextConfig, client, model, repo);
                String context = new ContextAggregator(sources).aggregate();

                if (context.isBlank()) {
                    System.out.println("[Scheduler] No new context found, skipping.");
                    return;
                }

                System.out.println("[Scheduler] Found context (" + context.length() + " chars), running compile...");
                var compilerSession = new IssueCompilerSession(client, model, repo, workingDir, dryRun);
                var issues = compilerSession.run(context);
                System.out.println("[Scheduler] Created " + issues.size() + " issue(s)");
            }
        } catch (Exception e) {
            System.err.println("[Scheduler] Poll failed: " + e.getMessage());
        }
    }

    public void shutdown() {
        System.out.println("[Scheduler] Shutting down...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
