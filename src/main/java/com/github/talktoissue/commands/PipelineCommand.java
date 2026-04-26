package com.github.talktoissue.commands;

import com.github.copilot.sdk.CopilotClient;
import com.github.talktoissue.App;
import com.github.talktoissue.CodeReviewSession;
import com.github.talktoissue.ImplementationSession;
import com.github.talktoissue.IntentDriftDetectorSession;
import com.github.talktoissue.IssueCompilerSession;
import com.github.talktoissue.IssueQualityScorerSession;
import com.github.talktoissue.IssueRefineSession;
import com.github.talktoissue.TranscriptFetchSession;
import com.github.talktoissue.VerificationSession;
import com.github.talktoissue.context.ContextAggregator;
import com.github.talktoissue.context.ContextConfig;
import com.github.talktoissue.context.ContextSource;
import com.github.talktoissue.tools.CreateIssueTool;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

    @Command(
    name = "pipeline",
    description = "Full pipeline: compile → score (with auto-refine) → implement (with verification) → drift detection → code review."
)
public class PipelineCommand implements Callable<Integer> {

    @ParentCommand
    private App parent;

    @Option(names = {"-f", "--file"},
            description = "Path to a context file (transcript, document, etc.)")
    private Path transcriptFile;

    @Option(names = {"-q", "--workiq-query"},
            description = "Natural language query to fetch context from Work IQ")
    private String workiqQuery;

    @Option(names = {"--tenant-id"},
            description = "Microsoft Entra tenant ID for Work IQ authentication")
    private String tenantId;

    @Option(names = {"--min-score"}, defaultValue = "70",
            description = "Minimum quality score to proceed with implementation. Default: ${DEFAULT-VALUE}")
    private int minScore;

    @Option(names = {"--context-config"},
            description = "Path to a YAML file defining multiple context sources")
    private Path contextConfig;

    @Option(names = {"--context"}, split = ",",
            description = "Inline context source(s): file:<path>, workiq:<query>, github:issues, github:prs")
    private List<String> contextSpecs;

    @Option(names = {"--max-refine-attempts"}, defaultValue = "3",
            description = "Maximum attempts to auto-refine issues below quality threshold. Default: ${DEFAULT-VALUE}")
    private int maxRefineAttempts;

    @Option(names = {"--max-fix-attempts"}, defaultValue = "3",
            description = "Maximum attempts to fix build/test failures after implementation. Default: ${DEFAULT-VALUE}")
    private int maxFixAttempts;

    @Option(names = {"--skip-review"},
            description = "Skip the self code review step")
    private boolean skipReview;

    @Override
    public Integer call() throws Exception {
        boolean hasLegacy = transcriptFile != null || workiqQuery != null;
        boolean hasNew = contextConfig != null || (contextSpecs != null && !contextSpecs.isEmpty());

        if (!hasLegacy && !hasNew) {
            System.err.println("Error: Specify context via --file, --workiq-query, --context-config, or --context.");
            return 1;
        }
        if (hasLegacy && hasNew) {
            System.err.println("Error: Cannot mix legacy options (--file/--workiq-query) with --context-config/--context.");
            return 1;
        }
        if (transcriptFile != null && workiqQuery != null) {
            System.err.println("Error: --file and --workiq-query are mutually exclusive.");
            return 1;
        }
        if (transcriptFile != null && !Files.exists(transcriptFile)) {
            System.err.println("Error: File not found: " + transcriptFile);
            return 1;
        }

        String token = System.getenv("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("Error: GITHUB_TOKEN environment variable is not set.");
            return 1;
        }

        File workingDir = parent.getWorkingDir();
        if (workingDir == null || !workingDir.isDirectory()) {
            System.err.println("Error: --working-dir is required for the pipeline command.");
            return 1;
        }

        String transcript = transcriptFile != null ? Files.readString(transcriptFile) : null;

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

            // Step 0: Resolve context
            String resolvedContext;
            if (hasNew) {
                List<ContextSource> sources;
                if (contextConfig != null) {
                    sources = ContextConfig.load(contextConfig, client, model, repository);
                } else {
                    sources = CompileCommand.parseInlineContextSpecs(contextSpecs, client, model, repository);
                }
                System.out.println("\n=== Step 0: Aggregating " + sources.size() + " context source(s) ===");
                resolvedContext = new ContextAggregator(sources).aggregate();
            } else if (transcript != null) {
                resolvedContext = transcript;
            } else {
                System.out.println("\n=== Step 0: Fetching context from Work IQ ===");
                var fetchSession = new TranscriptFetchSession(client, model, tenantId);
                resolvedContext = fetchSession.run(workiqQuery);
                System.out.println("Context fetched (" + resolvedContext.length() + " chars)");
            }

            // Step 1: Compile transcript into issues
            System.out.println("\n=== Step 1: Compiling transcript into coding-agent-ready issues ===");
            var compilerSession = new IssueCompilerSession(client, model, repository, workingDir, dryRun);
            List<CreateIssueTool.CreatedIssue> createdIssues = compilerSession.run(resolvedContext);

            System.out.println("Created " + createdIssues.size() + " issue(s)");
            if (createdIssues.isEmpty()) {
                System.out.println("No issues created. Pipeline complete.");
                return 0;
            }

            // Step 2: Score each issue (with auto-refine loop)
            System.out.println("\n=== Step 2: Scoring issue quality (min-score: " + minScore + ", max-refine: " + maxRefineAttempts + ") ===");
            var qualifiedIssues = new ArrayList<CreateIssueTool.CreatedIssue>();
            var skippedIssues = new ArrayList<CreateIssueTool.CreatedIssue>();

            for (var issue : createdIssues) {
                System.out.println("\nScoring Issue #" + issue.number() + ": " + issue.title());
                try {
                    var scorerSession = new IssueQualityScorerSession(client, model, repository, workingDir);
                    var score = scorerSession.run(issue.number());
                    System.out.println("  Score: " + score.overallScore() + "/100");

                    // Auto-refine loop: if below threshold, try to improve the issue
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
                        System.out.println("  ✓ Qualified for implementation"
                            + (refineAttempt > 0 ? " (after " + refineAttempt + " refinement(s))" : ""));
                        qualifiedIssues.add(issue);
                    } else {
                        System.out.println("  ✗ Below threshold (" + minScore + ") after " + refineAttempt + " refinement(s), skipping");
                        skippedIssues.add(issue);
                    }
                } catch (Exception e) {
                    System.err.println("  ⚠ Scoring failed: " + e.getMessage() + " — proceeding with implementation");
                    qualifiedIssues.add(issue);
                }
            }

            if (qualifiedIssues.isEmpty()) {
                System.out.println("\nNo issues passed quality threshold. Pipeline complete.");
                return 0;
            }

            // Step 3: Implement qualified issues (with build/test verification loop)
            System.out.println("\n=== Step 3: Implementing " + qualifiedIssues.size() + " qualified issue(s) (max-fix: " + maxFixAttempts + ") ===");

            record ImplResult(CreateIssueTool.CreatedIssue issue, boolean success, int prNumber) {}
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

                    // Step 3b: Verification loop — build and test, fix if needed
                    boolean verified = false;
                    for (int fixAttempt = 0; fixAttempt <= maxFixAttempts; fixAttempt++) {
                        System.out.println("  🔍 Verifying build and tests"
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

                            // Feed error context back to implementation session for self-correction
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
                                + "# ⚠ Previous implementation has errors. Fix them:\n\n"
                                + errorContext;

                            var fixSession = new ImplementationSession(client, model, repository, workingDir, dryRun);
                            fixSession.run(issue.number(), issue.title(), fixPrompt);
                        } catch (Exception e) {
                            System.err.println("  ⚠ Verification failed: " + e.getMessage());
                            break;
                        }
                    }

                    implResults.add(new ImplResult(issue, verified || dryRun, 0));
                    if (verified || dryRun) {
                        System.out.println("  ✓ Implementation succeeded.");
                    } else {
                        System.out.println("  ⚠ Implementation completed but verification failed.");
                    }
                } catch (Exception e) {
                    implResults.add(new ImplResult(issue, false, 0));
                    System.err.println("  ✗ Implementation failed: " + e.getMessage());
                }
            }

            // Step 4: Drift detection + code review for successful implementations
            var successfulImpls = implResults.stream().filter(ImplResult::success).toList();
            if (!successfulImpls.isEmpty() && !dryRun) {
                System.out.println("\n=== Step 4: Running drift detection & code review ===");
                for (var impl : successfulImpls) {
                    System.out.println("\nChecking drift for Issue #" + impl.issue().number());
                    try {
                        // Find the PR for this issue
                        var prs = repository.queryPullRequests()
                            .head(repository.getOwnerName() + ":issue-" + impl.issue().number())
                            .base("main")
                            .list().toList();

                        if (prs.isEmpty()) {
                            System.out.println("  ⚠ No PR found for branch issue-" + impl.issue().number());
                            continue;
                        }

                        int prNum = prs.get(0).getNumber();

                        // Step 4a: Drift detection
                        var driftSession = new IntentDriftDetectorSession(client, model, repository, workingDir);
                        var report = driftSession.run(prNum, impl.issue().number(), resolvedContext);

                        String icon = switch (report.verdict()) {
                            case "pass" -> "✓";
                            case "warn" -> "⚠";
                            case "fail" -> "✗";
                            default -> "?";
                        };
                        System.out.println("  " + icon + " Drift: " + report.verdict().toUpperCase()
                            + " (" + report.drifts().size() + " drift(s))");

                        // Step 4b: Self code review
                        if (!skipReview) {
                            System.out.println("  📝 Running self code review...");
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
                                    + " (" + criticalCount + " critical, " + warningCount + " warning, "
                                    + review.findings().size() + " total)");

                                if (!review.positives().isEmpty()) {
                                    System.out.println("  Positives:");
                                    for (var p : review.positives()) {
                                        System.out.println("    + " + p);
                                    }
                                }
                            } catch (Exception e) {
                                System.err.println("  ⚠ Code review failed: " + e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("  ⚠ Drift detection failed: " + e.getMessage());
                    }
                }
            } else if (dryRun) {
                System.out.println("\n=== Step 4: Drift detection & code review skipped (dry-run) ===");
            }

            // Summary
            long successCount = implResults.stream().filter(ImplResult::success).count();
            long failCount = implResults.stream().filter(r -> !r.success()).count();

            System.out.println("\n=== Pipeline Summary ===");
            System.out.println("Issues compiled:    " + createdIssues.size());
            System.out.println("Issues qualified:   " + qualifiedIssues.size() + " (min-score: " + minScore + ")");
            System.out.println("Issues skipped:     " + skippedIssues.size());
            System.out.println("Implementations:    " + successCount + " succeeded, " + failCount + " failed");
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
}
