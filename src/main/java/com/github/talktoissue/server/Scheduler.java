package com.github.talktoissue.server;

import com.github.copilot.sdk.CopilotClient;
import com.github.talktoissue.CodebaseAnalysisSession;
import com.github.talktoissue.CodeReviewSession;
import com.github.talktoissue.ImplementationSession;
import com.github.talktoissue.IntentDriftDetectorSession;
import com.github.talktoissue.IssueQualityScorerSession;
import com.github.talktoissue.IssueRefineSession;
import com.github.talktoissue.PrioritizationSession;
import com.github.talktoissue.SpecDesignSession;
import com.github.talktoissue.VerificationSession;
import com.github.talktoissue.context.ContextAggregator;
import com.github.talktoissue.context.ContextConfig;
import com.github.talktoissue.IssueCompilerSession;
import com.github.talktoissue.tools.CreateIssueTool;
import com.github.talktoissue.tools.ReportDiscoveryTool;
import com.github.talktoissue.tools.ReportPrioritizationTool;
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
     * Schedule an autonomous improvement cycle at the given interval.
     */
    public void startAutonomousCycle(int intervalMinutes, int maxIssues, int minScore) {
        System.out.println("[Scheduler] Starting autonomous cycle with " + intervalMinutes + " minute interval"
            + " (max-issues: " + maxIssues + ", min-score: " + minScore + ")");
        scheduler.scheduleAtFixedRate(
            () -> runAutonomousCycle(maxIssues, minScore),
            0, intervalMinutes, TimeUnit.MINUTES
        );
    }

    private void runAutonomousCycle(int maxIssues, int minScore) {
        System.out.println("[Scheduler] Running autonomous improvement cycle...");
        try {
            String token = System.getenv("GITHUB_TOKEN");
            GitHub gitHub = new GitHubBuilder().withOAuthToken(token).build();
            GHRepository repo = gitHub.getRepository(repoFullName);

            try (var client = new CopilotClient()) {
                client.start().get();

                // Step 1: Analyze codebase
                System.out.println("[Scheduler] Step 1: Analyzing codebase...");
                var analysisSession = new CodebaseAnalysisSession(client, model, workingDir, null);
                List<ReportDiscoveryTool.Discovery> discoveries = analysisSession.run();

                if (discoveries.isEmpty()) {
                    System.out.println("[Scheduler] No improvements found, skipping.");
                    return;
                }
                System.out.println("[Scheduler] Found " + discoveries.size() + " improvement(s)");

                // Step 2: Prioritize
                System.out.println("[Scheduler] Step 2: Prioritizing...");
                List<ReportPrioritizationTool.PrioritizedItem> prioritized;
                if (discoveries.size() <= maxIssues) {
                    prioritized = discoveries.stream()
                        .map(d -> new ReportPrioritizationTool.PrioritizedItem(
                            d.title(), d.description(), d.category(),
                            discoveries.indexOf(d) + 1, "Within limit"))
                        .toList();
                } else {
                    var prioritizationSession = new PrioritizationSession(client, model);
                    prioritized = prioritizationSession.run(discoveries, maxIssues);
                }

                // Step 3: Design specs and create issues
                System.out.println("[Scheduler] Step 3: Creating issues...");
                var allIssues = new ArrayList<CreateIssueTool.CreatedIssue>();
                for (var item : prioritized) {
                    try {
                        var specSession = new SpecDesignSession(client, model, repo, workingDir, dryRun);
                        allIssues.addAll(specSession.run(item));
                    } catch (Exception e) {
                        System.err.println("[Scheduler] Spec design failed for: " + item.title() + " — " + e.getMessage());
                    }
                }

                // Step 4: Score, refine, implement, verify each issue
                for (var issue : allIssues) {
                    try {
                        System.out.println("[Scheduler] Processing Issue #" + issue.number() + ": " + issue.title());

                        // Score & refine
                        var scorer = new IssueQualityScorerSession(client, model, repo, workingDir);
                        var score = scorer.run(issue.number());
                        int attempts = 0;
                        while (score.overallScore() < minScore && attempts < 3) {
                            attempts++;
                            var refiner = new IssueRefineSession(client, model, repo, workingDir, dryRun);
                            refiner.run(issue.number(), score);
                            scorer = new IssueQualityScorerSession(client, model, repo, workingDir);
                            score = scorer.run(issue.number());
                        }

                        if (score.overallScore() < minScore) {
                            System.out.println("[Scheduler] Issue #" + issue.number() + " below threshold, skipping.");
                            continue;
                        }

                        // Implement
                        resetToMain();
                        String issueBody = dryRun ? issue.title() : repo.getIssue(issue.number()).getBody();
                        var impl = new ImplementationSession(client, model, repo, workingDir, dryRun);
                        impl.run(issue.number(), issue.title(), issueBody != null ? issueBody : issue.title());

                        // Verify
                        var verifier = new VerificationSession(client, model, workingDir, dryRun);
                        var result = verifier.run();
                        if (!result.buildSuccess() || !result.testsSuccess()) {
                            System.out.println("[Scheduler] Verification failed for Issue #" + issue.number());
                        }

                        // Drift + Review
                        if (!dryRun) {
                            var prs = repo.queryPullRequests()
                                .head(repo.getOwnerName() + ":issue-" + issue.number())
                                .base("main").list().toList();
                            if (!prs.isEmpty()) {
                                var drift = new IntentDriftDetectorSession(client, model, repo, workingDir);
                                drift.run(prs.get(0).getNumber(), issue.number(), null);
                                var review = new CodeReviewSession(client, model, repo, workingDir);
                                review.run(prs.get(0).getNumber());
                            }
                        }

                        System.out.println("[Scheduler] Completed Issue #" + issue.number());
                    } catch (Exception e) {
                        System.err.println("[Scheduler] Failed processing Issue #" + issue.number() + ": " + e.getMessage());
                    }
                }

                System.out.println("[Scheduler] Autonomous cycle complete. Processed " + allIssues.size() + " issue(s).");
            }
        } catch (Exception e) {
            System.err.println("[Scheduler] Autonomous cycle failed: " + e.getMessage());
        }
    }

    private void resetToMain() throws Exception {
        var process = new ProcessBuilder("git", "checkout", "main")
            .directory(workingDir)
            .redirectErrorStream(true)
            .start();
        process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Failed to checkout main branch");
        }
    }
}
