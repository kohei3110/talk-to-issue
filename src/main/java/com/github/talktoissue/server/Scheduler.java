package com.github.talktoissue.server;

import com.github.copilot.sdk.CopilotClient;
import com.github.talktoissue.CodebaseAnalysisSession;
import com.github.talktoissue.PrioritizationSession;
import com.github.talktoissue.SpecDesignSession;
import com.github.talktoissue.context.ContextAggregator;
import com.github.talktoissue.context.ContextConfig;
import com.github.talktoissue.IssueCompilerSession;
import com.github.talktoissue.IssueQualityScorerSession;
import com.github.talktoissue.IssueRefineSession;
import com.github.talktoissue.ImplementationSession;
import com.github.talktoissue.VerificationSession;
import com.github.talktoissue.tools.CreateIssueTool;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * Start the autonomous cycle that analyzes the codebase, creates issues,
     * implements them, and runs reviews on a fixed interval.
     *
     * @param intervalMinutes how often to run the cycle, in minutes
     * @param maxIssues       maximum number of issues per cycle
     * @param minScore        minimum quality score for implementation
     */
    public void startAutonomousCycle(int intervalMinutes, int maxIssues, int minScore) {
        System.out.println("[Scheduler] Starting autonomous cycle with " + intervalMinutes + " minute interval");
        // Run once immediately, then at fixed intervals
        scheduler.scheduleAtFixedRate(
            () -> runAutonomousCycle(maxIssues, minScore),
            0, intervalMinutes, TimeUnit.MINUTES
        );
    }

    private void runAutonomousCycle(int maxIssues, int minScore) {
        System.out.println("[Scheduler] Running autonomous cycle...");
        try {
            String token = System.getenv("GITHUB_TOKEN");
            GitHub gitHub = new GitHubBuilder().withOAuthToken(token).build();
            GHRepository repo = gitHub.getRepository(repoFullName);

            try (var client = new CopilotClient()) {
                client.start().get();

                // Step 1: Analysis
                System.out.println("[Autonomous] Step 1: Analyzing codebase...");
                var analysisSession = new CodebaseAnalysisSession(client, model, workingDir, null);
                var discoveries = analysisSession.run();
                System.out.println("[Autonomous] Found " + discoveries.size() + " improvement(s)");

                if (discoveries.isEmpty()) {
                    System.out.println("[Autonomous] No improvements found. Cycle complete.");
                    return;
                }

                // Step 2: Prioritization
                System.out.println("[Autonomous] Step 2: Prioritizing...");
                var prioritizationSession = new PrioritizationSession(client, model);
                var prioritized = prioritizationSession.run(discoveries, maxIssues);
                System.out.println("[Autonomous] Selected " + prioritized.size() + " item(s)");

                if (prioritized.isEmpty()) return;

                // Step 3: Spec Design
                System.out.println("[Autonomous] Step 3: Creating issues...");
                var allCreatedIssues = new ArrayList<CreateIssueTool.CreatedIssue>();
                for (var item : prioritized) {
                    try {
                        var specSession = new SpecDesignSession(client, model, repo, workingDir, dryRun);
                        allCreatedIssues.addAll(specSession.run(item));
                    } catch (Exception e) {
                        System.err.println("[Autonomous] Spec design failed: " + e.getMessage());
                    }
                }

                if (allCreatedIssues.isEmpty()) return;

                // Step 4: Score & Refine
                System.out.println("[Autonomous] Step 4: Scoring issues...");
                var qualifiedIssues = new ArrayList<CreateIssueTool.CreatedIssue>();
                for (var issue : allCreatedIssues) {
                    try {
                        var scorer = new IssueQualityScorerSession(client, model, repo, workingDir);
                        var score = scorer.run(issue.number());
                        int attempts = 0;
                        while (score.overallScore() < minScore && attempts < 3) {
                            attempts++;
                            try {
                                new IssueRefineSession(client, model, repo, workingDir, dryRun)
                                    .run(issue.number(), score);
                                score = new IssueQualityScorerSession(client, model, repo, workingDir)
                                    .run(issue.number());
                            } catch (Exception e) {
                                break;
                            }
                        }
                        if (score.overallScore() >= minScore) {
                            qualifiedIssues.add(issue);
                        }
                    } catch (Exception e) {
                        qualifiedIssues.add(issue);
                    }
                }

                if (qualifiedIssues.isEmpty()) return;

                // Step 5: Implement & Verify
                System.out.println("[Autonomous] Step 5: Implementing " + qualifiedIssues.size() + " issue(s)...");
                for (var issue : qualifiedIssues) {
                    try {
                        String issueBody;
                        if (dryRun) {
                            issueBody = "Dry-run: " + issue.title();
                        } else {
                            var ghIssue = repo.getIssue(issue.number());
                            issueBody = ghIssue.getBody() != null ? ghIssue.getBody() : issue.title();
                        }
                        var implSession = new ImplementationSession(client, model, repo, workingDir, dryRun);
                        implSession.run(issue.number(), issue.title(), issueBody);

                        for (int fix = 0; fix <= 3; fix++) {
                            var result = new VerificationSession(client, model, workingDir, dryRun).run();
                            if (result.buildSuccess() && result.testsSuccess()) break;
                            if (fix >= 3) break;
                            var fixSession = new ImplementationSession(client, model, repo, workingDir, dryRun);
                            fixSession.run(issue.number(), issue.title(), issueBody + "\n\nFix errors.");
                        }
                    } catch (Exception e) {
                        System.err.println("[Autonomous] Implementation failed for #" + issue.number() + ": " + e.getMessage());
                    }
                }

                System.out.println("[Autonomous] Cycle complete.");
            }
        } catch (Exception e) {
            System.err.println("[Autonomous] Cycle failed: " + e.getMessage());
        }
    }
}
