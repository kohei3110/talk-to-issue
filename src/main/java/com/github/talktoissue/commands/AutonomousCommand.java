package com.github.talktoissue.commands;

import com.github.copilot.sdk.CopilotClient;
import com.github.talktoissue.App;
import com.github.talktoissue.CodebaseAnalysisSession;
import com.github.talktoissue.CodeReviewSession;
import com.github.talktoissue.ImplementationSession;
import com.github.talktoissue.IntentDriftDetectorSession;
import com.github.talktoissue.IssueQualityScorerSession;
import com.github.talktoissue.IssueRefineSession;
import com.github.talktoissue.PrioritizationSession;
import com.github.talktoissue.SpecDesignSession;
import com.github.talktoissue.VerificationSession;
import com.github.talktoissue.tools.CreateIssueTool;
import com.github.talktoissue.tools.ReportDiscoveryTool;
import com.github.talktoissue.tools.ReportPrioritizationTool;
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
    description = "Autonomous improvement cycle: analyze codebase → prioritize → design issues → score/refine → implement → verify → review."
)
public class AutonomousCommand implements Callable<Integer> {

    @ParentCommand
    private App parent;

    @Option(names = {"--max-issues"}, defaultValue = "3",
            description = "Maximum number of issues to create and implement. Default: ${DEFAULT-VALUE}")
    private int maxIssues;

    @Option(names = {"--min-score"}, defaultValue = "70",
            description = "Minimum quality score for issues to proceed with implementation. Default: ${DEFAULT-VALUE}")
    private int minScore;

    @Option(names = {"--max-refine-attempts"}, defaultValue = "3",
            description = "Maximum attempts to auto-refine issues below quality threshold. Default: ${DEFAULT-VALUE}")
    private int maxRefineAttempts;

    @Option(names = {"--max-fix-attempts"}, defaultValue = "3",
            description = "Maximum attempts to fix build/test failures after implementation. Default: ${DEFAULT-VALUE}")
    private int maxFixAttempts;

    @Option(names = {"--skip-review"},
            description = "Skip the self code review step")
    private boolean skipReview;

    @Option(names = {"--categories"}, split = ",",
            description = "Comma-separated categories to focus on: todo,test_gap,security,tech_debt,error_handling,documentation")
    private List<String> categories;

    @Override
    public Integer call() throws Exception {
        String token = System.getenv("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("Error: GITHUB_TOKEN environment variable is not set.");
            return 1;
        }

        File workingDir = parent.getWorkingDir();
        if (workingDir == null || !workingDir.isDirectory()) {
            System.err.println("Error: --working-dir is required for the autonomous command.");
            return 1;
        }

        GitHub gitHub = new GitHubBuilder().withOAuthToken(token).build();
        var repository = gitHub.getRepository(parent.getRepoFullName());
        System.out.println("Connected to repository: " + repository.getFullName());

        boolean dryRun = parent.isDryRun();
        String model = parent.getModel();

        if (dryRun) {
            System.out.println("=== DRY-RUN MODE ===");
        }

        try (var client = new CopilotClient()) {
            client.start().get();
            System.out.println("Copilot SDK started.");

            // Step 1: Codebase Analysis
            System.out.println("\n┌─────────────────────────────────────────────────┐");
            System.out.println("│ Step 1: Codebase Analysis                       │");
            System.out.println("└─────────────────────────────────────────────────┘");
            var analysisSession = new CodebaseAnalysisSession(client, model, workingDir, categories);
            List<ReportDiscoveryTool.Discovery> discoveries = analysisSession.run();

            System.out.println("Discovered " + discoveries.size() + " improvement opportunity(ies).");
            if (discoveries.isEmpty()) {
                System.out.println("No improvements found. Autonomous cycle complete.");
                return 0;
            }

            // Step 2: Prioritization
            System.out.println("\n┌─────────────────────────────────────────────────┐");
            System.out.println("│ Step 2: Prioritization                          │");
            System.out.println("└─────────────────────────────────────────────────┘");
            List<ReportPrioritizationTool.PrioritizedItem> prioritizedItems;
            if (discoveries.size() <= maxIssues) {
                System.out.println("Discoveries (" + discoveries.size() + ") ≤ max-issues (" + maxIssues + "), skipping prioritization.");
                prioritizedItems = new ArrayList<>();
                for (int i = 0; i < discoveries.size(); i++) {
                    var d = discoveries.get(i);
                    prioritizedItems.add(new ReportPrioritizationTool.PrioritizedItem(
                        d.title(), d.description(), d.category(), i + 1,
                        "Auto-included (total discoveries within limit)"
                    ));
                }
            } else {
                var prioritizationSession = new PrioritizationSession(client, model);
                prioritizedItems = prioritizationSession.run(discoveries, maxIssues);
                System.out.println("Selected " + prioritizedItems.size() + " item(s) for implementation.");
            }

            if (prioritizedItems.isEmpty()) {
                System.out.println("No items selected. Autonomous cycle complete.");
                return 0;
            }

            // Step 3: Spec Design → Create Issues
            System.out.println("\n┌─────────────────────────────────────────────────┐");
            System.out.println("│ Step 3: Issue Specification Design              │");
            System.out.println("└─────────────────────────────────────────────────┘");
            var allCreatedIssues = new ArrayList<CreateIssueTool.CreatedIssue>();

            for (var item : prioritizedItems) {
                System.out.println("\nDesigning spec for: " + item.title());
                try {
                    var specSession = new SpecDesignSession(client, model, repository, workingDir, dryRun);
                    var issues = specSession.run(item);
                    allCreatedIssues.addAll(issues);
                    for (var issue : issues) {
                        System.out.println("  Created Issue #" + issue.number() + ": " + issue.title());
                    }
                } catch (Exception e) {
                    System.err.println("  ✗ Spec design failed: " + e.getMessage());
                }
            }

            if (allCreatedIssues.isEmpty()) {
                System.out.println("No issues created. Autonomous cycle complete.");
                return 0;
            }

            // Step 4: Score & Refine
            System.out.println("\n┌─────────────────────────────────────────────────┐");
            System.out.println("│ Step 4: Quality Scoring & Auto-Refine           │");
            System.out.println("└─────────────────────────────────────────────────┘");
            var qualifiedIssues = new ArrayList<CreateIssueTool.CreatedIssue>();
            var skippedIssues = new ArrayList<CreateIssueTool.CreatedIssue>();

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
                            System.out.println("  Issue refined. Re-scoring...");

                            scorerSession = new IssueQualityScorerSession(client, model, repository, workingDir);
                            score = scorerSession.run(issue.number());
                            System.out.println("  New score: " + score.overallScore() + "/100");
                        } catch (Exception e) {
                            System.err.println("  ⚠ Refine attempt " + refineAttempt + " failed: " + e.getMessage());
                            break;
                        }
                    }

                    if (score.overallScore() >= minScore) {
                        System.out.println("  ✓ Qualified"
                            + (refineAttempt > 0 ? " (after " + refineAttempt + " refinement(s))" : ""));
                        qualifiedIssues.add(issue);
                    } else {
                        System.out.println("  ✗ Below threshold (" + minScore + "), skipping");
                        skippedIssues.add(issue);
                    }
                } catch (Exception e) {
                    System.err.println("  ⚠ Scoring failed: " + e.getMessage() + " — proceeding anyway");
                    qualifiedIssues.add(issue);
                }
            }

            if (qualifiedIssues.isEmpty()) {
                System.out.println("\nNo issues passed quality threshold. Autonomous cycle complete.");
                return 0;
            }

            // Step 5: Implementation with verification loop
            System.out.println("\n┌─────────────────────────────────────────────────┐");
            System.out.println("│ Step 5: Implementation & Verification           │");
            System.out.println("└─────────────────────────────────────────────────┘");

            record ImplResult(CreateIssueTool.CreatedIssue issue, boolean success) {}
            var implResults = new ArrayList<ImplResult>();

            for (var issue : qualifiedIssues) {
                System.out.println("\n--- Implementing Issue #" + issue.number() + ": " + issue.title() + " ---");
                try {
                    resetToMain(workingDir);

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
                            if (!result.suggestions().isEmpty()) {
                                errorContext.append("\n## Suggestions\n");
                                for (var s : result.suggestions()) {
                                    errorContext.append("- ").append(s).append("\n");
                                }
                            }

                            String fixPrompt = issueBody + "\n\n---\n\n"
                                + "# Previous implementation has errors. Fix them:\n\n"
                                + errorContext;

                            var fixSession = new ImplementationSession(client, model, repository, workingDir, dryRun);
                            fixSession.run(issue.number(), issue.title(), fixPrompt);
                        } catch (Exception e) {
                            System.err.println("  ⚠ Verification failed: " + e.getMessage());
                            break;
                        }
                    }

                    implResults.add(new ImplResult(issue, verified || dryRun));
                    if (verified || dryRun) {
                        System.out.println("  ✓ Implementation succeeded.");
                    } else {
                        System.out.println("  ⚠ Implementation completed but verification failed.");
                    }
                } catch (Exception e) {
                    implResults.add(new ImplResult(issue, false));
                    System.err.println("  ✗ Implementation failed: " + e.getMessage());
                }
            }

            // Step 6: Drift detection + code review
            var successfulImpls = implResults.stream().filter(ImplResult::success).toList();
            if (!successfulImpls.isEmpty() && !dryRun && !skipReview) {
                System.out.println("\n┌─────────────────────────────────────────────────┐");
                System.out.println("│ Step 6: Drift Detection & Code Review           │");
                System.out.println("└─────────────────────────────────────────────────┘");

                for (var impl : successfulImpls) {
                    System.out.println("\nChecking Issue #" + impl.issue().number());
                    try {
                        var prs = repository.queryPullRequests()
                            .head(repository.getOwnerName() + ":issue-" + impl.issue().number())
                            .base("main")
                            .list().toList();

                        if (prs.isEmpty()) {
                            System.out.println("  ⚠ No PR found for branch issue-" + impl.issue().number());
                            continue;
                        }

                        int prNum = prs.get(0).getNumber();

                        var driftSession = new IntentDriftDetectorSession(client, model, repository, workingDir);
                        var report = driftSession.run(prNum, impl.issue().number(), "autonomous improvement");

                        String icon = switch (report.verdict()) {
                            case "pass" -> "✓";
                            case "warn" -> "⚠";
                            case "fail" -> "✗";
                            default -> "?";
                        };
                        System.out.println("  " + icon + " Drift: " + report.verdict().toUpperCase()
                            + " (" + report.drifts().size() + " drift(s))");

                        System.out.println("  Running self code review...");
                        try {
                            var reviewSession = new CodeReviewSession(client, model, repository, workingDir);
                            var review = reviewSession.run(prNum);

                            String reviewIcon = switch (review.verdict()) {
                                case "approve" -> "✓";
                                case "request_changes" -> "✗";
                                case "comment" -> "💬";
                                default -> "?";
                            };
                            long criticalCount = review.findings().stream()
                                .filter(f -> "critical".equals(f.severity())).count();
                            long warningCount = review.findings().stream()
                                .filter(f -> "warning".equals(f.severity())).count();

                            System.out.println("  " + reviewIcon + " Review: " + review.verdict().toUpperCase()
                                + " (" + criticalCount + " critical, " + warningCount + " warning)");
                        } catch (Exception e) {
                            System.err.println("  ⚠ Code review failed: " + e.getMessage());
                        }
                    } catch (Exception e) {
                        System.err.println("  ⚠ Post-implementation check failed: " + e.getMessage());
                    }
                }
            }

            // Summary
            long successCount = implResults.stream().filter(ImplResult::success).count();
            long failCount = implResults.stream().filter(r -> !r.success()).count();

            System.out.println("\n┌─────────────────────────────────────────────────┐");
            System.out.println("│ Autonomous Cycle Summary                        │");
            System.out.println("├─────────────────────────────────────────────────┤");
            System.out.println("│ Discoveries:      " + padRight(String.valueOf(discoveries.size()), 30) + "│");
            System.out.println("│ Prioritized:      " + padRight(String.valueOf(prioritizedItems.size()), 30) + "│");
            System.out.println("│ Issues created:   " + padRight(String.valueOf(allCreatedIssues.size()), 30) + "│");
            System.out.println("│ Issues qualified: " + padRight(String.valueOf(qualifiedIssues.size()), 30) + "│");
            System.out.println("│ Issues skipped:   " + padRight(String.valueOf(skippedIssues.size()), 30) + "│");
            System.out.println("│ Implemented OK:   " + padRight(String.valueOf(successCount), 30) + "│");
            System.out.println("│ Implemented FAIL: " + padRight(String.valueOf(failCount), 30) + "│");
            System.out.println("└─────────────────────────────────────────────────┘");
        }

        return 0;
    }

    private void resetToMain(File workingDir) throws Exception {
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

    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}
