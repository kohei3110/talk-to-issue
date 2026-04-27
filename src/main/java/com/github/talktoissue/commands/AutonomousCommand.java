package com.github.talktoissue.commands;

import com.github.copilot.sdk.CopilotClient;
import com.github.talktoissue.App;
import com.github.talktoissue.CodebaseAnalysisSession;
import com.github.talktoissue.ImplementationSession;
import com.github.talktoissue.IssueQualityScorerSession;
import com.github.talktoissue.IssueRefineSession;
import com.github.talktoissue.PrioritizationSession;
import com.github.talktoissue.SpecDesignSession;
import com.github.talktoissue.VerificationSession;
import com.github.talktoissue.tools.CreateIssueTool;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "autonomous",
    description = "Autonomously analyze the codebase, discover improvements, create issues, implement, and review."
)
public class AutonomousCommand implements Callable<Integer> {

    @ParentCommand
    private App parent;

    @Option(names = {"--max-issues"}, defaultValue = "3",
            description = "Maximum number of issues to create per cycle. Default: ${DEFAULT-VALUE}")
    private int maxIssues;

    @Option(names = {"--min-score"}, defaultValue = "70",
            description = "Minimum quality score to proceed with implementation. Default: ${DEFAULT-VALUE}")
    private int minScore;

    @Option(names = {"--max-refine-attempts"}, defaultValue = "3",
            description = "Maximum attempts to auto-refine issues below quality threshold. Default: ${DEFAULT-VALUE}")
    private int maxRefineAttempts;

    @Option(names = {"--max-fix-attempts"}, defaultValue = "3",
            description = "Maximum attempts to fix build/test failures. Default: ${DEFAULT-VALUE}")
    private int maxFixAttempts;

    @Option(names = {"--categories"}, split = ",",
            description = "Limit analysis to specific categories: todo,test_gap,security,tech_debt,error_handling,documentation")
    private List<String> categories;

    @Override
    public Integer call() throws Exception {
        String token = System.getenv("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("Error: GITHUB_TOKEN environment variable is not set.");
            return 1;
        }

        if (parent.isRepoRequired()) {
            System.err.println("Error: --repo is required for the autonomous command.");
            return 1;
        }

        File workingDir = parent.getWorkingDir();
        if (workingDir == null || !workingDir.isDirectory()) {
            System.err.println("Error: --working-dir is required for the autonomous command.");
            return 1;
        }

        String model = parent.getModel();
        boolean dryRun = parent.isDryRun();

        GitHub gitHub = new GitHubBuilder().withOAuthToken(token).build();
        var repository = gitHub.getRepository(parent.getRepoFullName());
        System.out.println("Connected to repository: " + repository.getFullName());

        if (dryRun) {
            System.out.println("=== DRY-RUN MODE ===");
        }

        try (var client = new CopilotClient()) {
            client.start().get();
            System.out.println("Copilot SDK started.");

            return runAutonomousCycle(client, model, dryRun, repository, workingDir);
        }
    }

    public int runAutonomousCycle(CopilotClient client, String model, boolean dryRun,
                                   org.kohsuke.github.GHRepository repository, File workingDir) throws Exception {
        // Step 1: Codebase Analysis
        System.out.println("\n=== Step 1: Codebase Analysis ===");
        var analysisSession = new CodebaseAnalysisSession(client, model, workingDir, categories);
        var discoveries = analysisSession.run();
        System.out.println("Discovered " + discoveries.size() + " improvement opportunity(ies)");

        if (discoveries.isEmpty()) {
            System.out.println("No improvements found. Cycle complete.");
            return 0;
        }

        // Step 2: Prioritization
        System.out.println("\n=== Step 2: Prioritization ===");
        var prioritizationSession = new PrioritizationSession(client, model);
        var prioritized = prioritizationSession.run(discoveries, maxIssues);
        System.out.println("Selected " + prioritized.size() + " item(s) for implementation");

        if (prioritized.isEmpty()) {
            System.out.println("No items selected. Cycle complete.");
            return 0;
        }

        // Step 3: Spec Design — create issues
        System.out.println("\n=== Step 3: Spec Design (Creating Issues) ===");
        var allCreatedIssues = new ArrayList<CreateIssueTool.CreatedIssue>();
        for (var item : prioritized) {
            System.out.println("\nDesigning spec for: " + item.title());
            try {
                var specSession = new SpecDesignSession(client, model, repository, workingDir, dryRun);
                var issues = specSession.run(item);
                allCreatedIssues.addAll(issues);
                System.out.println("  Created " + issues.size() + " issue(s)");
            } catch (Exception e) {
                System.err.println("  ⚠ Spec design failed: " + e.getMessage());
            }
        }

        if (allCreatedIssues.isEmpty()) {
            System.out.println("No issues created. Cycle complete.");
            return 0;
        }

        // Step 4: Score & Refine
        System.out.println("\n=== Step 4: Quality Scoring (min-score: " + minScore + ") ===");
        var qualifiedIssues = new ArrayList<CreateIssueTool.CreatedIssue>();
        for (var issue : allCreatedIssues) {
            System.out.println("\nScoring Issue #" + issue.number() + ": " + issue.title());
            try {
                var scorerSession = new IssueQualityScorerSession(client, model, repository, workingDir);
                var score = scorerSession.run(issue.number());
                System.out.println("  Score: " + score.overallScore() + "/100");

                int refineAttempt = 0;
                while (score.overallScore() < minScore && refineAttempt < maxRefineAttempts) {
                    refineAttempt++;
                    System.out.println("  ↻ Auto-refining (attempt " + refineAttempt + "/" + maxRefineAttempts + ")...");
                    try {
                        var refineSession = new IssueRefineSession(client, model, repository, workingDir, dryRun);
                        refineSession.run(issue.number(), score);
                        scorerSession = new IssueQualityScorerSession(client, model, repository, workingDir);
                        score = scorerSession.run(issue.number());
                        System.out.println("  New score: " + score.overallScore() + "/100");
                    } catch (Exception e) {
                        System.err.println("  ⚠ Refine attempt " + refineAttempt + " failed: " + e.getMessage());
                        break;
                    }
                }

                if (score.overallScore() >= minScore) {
                    System.out.println("  ✓ Qualified");
                    qualifiedIssues.add(issue);
                } else {
                    System.out.println("  ✗ Below threshold, skipping");
                }
            } catch (Exception e) {
                System.err.println("  ⚠ Scoring failed: " + e.getMessage() + " — proceeding anyway");
                qualifiedIssues.add(issue);
            }
        }

        if (qualifiedIssues.isEmpty()) {
            System.out.println("\nNo issues qualified. Cycle complete.");
            return 0;
        }

        // Step 5: Implement & Verify
        System.out.println("\n=== Step 5: Implementing " + qualifiedIssues.size() + " issue(s) ===");

        record ImplResult(CreateIssueTool.CreatedIssue issue, boolean success) {}
        var implResults = new ArrayList<ImplResult>();

        for (var issue : qualifiedIssues) {
            System.out.println("\n--- Implementing Issue #" + issue.number() + ": " + issue.title() + " ---");
            try {

                String issueBody;
                if (dryRun) {
                    issueBody = "Dry-run issue body for: " + issue.title();
                } else {
                    var ghIssue = repository.getIssue(issue.number());
                    issueBody = ghIssue.getBody() != null ? ghIssue.getBody() : issue.title();
                }

                var implSession = new ImplementationSession(client, model, repository, workingDir, dryRun);
                implSession.run(issue.number(), issue.title(), issueBody);

                boolean verified = false;
                for (int fixAttempt = 0; fixAttempt <= maxFixAttempts; fixAttempt++) {
                    System.out.println("  Verifying build and tests"
                        + (fixAttempt > 0 ? " (fix attempt " + fixAttempt + "/" + maxFixAttempts + ")" : "") + "...");
                    try {
                        var verifySession = new VerificationSession(client, model, workingDir, dryRun);
                        var result = verifySession.run();

                        if (result.buildSuccess() && result.testsSuccess()) {
                            System.out.println("  ✓ Build and tests passed.");
                            verified = true;
                            break;
                        }

                        if (fixAttempt >= maxFixAttempts) {
                            System.out.println("  ✗ Build/tests still failing after " + maxFixAttempts + " fix attempt(s).");
                            break;
                        }

                        System.out.println("  ↻ Build/tests failed. Running self-correction...");
                        var errorContext = new StringBuilder();
                        if (!result.buildSuccess()) {
                            errorContext.append("## Build Errors\n").append(result.buildOutput()).append("\n\n");
                        }
                        if (!result.testsSuccess()) {
                            errorContext.append("## Test Failures\n").append(result.testOutput()).append("\n\n");
                            for (var failure : result.failures()) {
                                errorContext.append("- ").append(failure.testName())
                                    .append(": ").append(failure.message()).append("\n");
                            }
                        }

                        String fixPrompt = issueBody + "\n\n---\n\n"
                            + "# ⚠ Previous implementation has errors. Fix them:\n\n"
                            + errorContext;

                        var fixSession = new ImplementationSession(client, model, repository, workingDir, dryRun);
                        fixSession.run(issue.number(), issue.title(), fixPrompt);
                    } catch (Exception e) {
                        System.err.println("  ⚠ Verification failed: " + e.getMessage());
                        break;
                    }
                }

                implResults.add(new ImplResult(issue, verified || dryRun));
            } catch (Exception e) {
                implResults.add(new ImplResult(issue, false));
                System.err.println("  ✗ Implementation failed: " + e.getMessage());
            }
        }

        // Summary
        long successCount = implResults.stream().filter(ImplResult::success).count();
        long failCount = implResults.stream().filter(r -> !r.success()).count();

        System.out.println("\n=== Autonomous Cycle Summary ===");
        System.out.println("Discoveries:        " + discoveries.size());
        System.out.println("Prioritized:        " + prioritized.size());
        System.out.println("Issues created:     " + allCreatedIssues.size());
        System.out.println("Issues qualified:   " + qualifiedIssues.size());
        System.out.println("Implementations:    " + successCount + " succeeded, " + failCount + " failed");

        return 0;
    }
}
