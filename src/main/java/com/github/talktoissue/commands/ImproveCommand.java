package com.github.talktoissue.commands;

import com.github.copilot.sdk.CopilotClient;
import com.github.talktoissue.App;
import com.github.talktoissue.CodebaseAnalysisSession;
import com.github.talktoissue.LocalImplementationSession;
import com.github.talktoissue.PrioritizationSession;
import com.github.talktoissue.VerificationSession;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@Command(
    name = "improve",
    description = "Autonomously analyze a local codebase and continuously implement improvements without GitHub."
)
public class ImproveCommand implements Callable<Integer> {

    @ParentCommand
    private App parent;

    @Option(names = {"--target-dir"}, required = true,
            description = "Path to the local project directory to improve")
    private File targetDir;

    @Option(names = {"--max-issues"}, defaultValue = "3",
            description = "Maximum number of improvements per cycle. Default: ${DEFAULT-VALUE}")
    private int maxIssues;

    @Option(names = {"--max-fix-attempts"}, defaultValue = "3",
            description = "Maximum attempts to fix build/test failures. Default: ${DEFAULT-VALUE}")
    private int maxFixAttempts;

    @Option(names = {"--categories"}, split = ",",
            description = "Limit analysis to specific categories: todo,test_gap,security,tech_debt,error_handling,documentation")
    private List<String> categories;

    @Option(names = {"--interval"}, defaultValue = "1",
            description = "Minutes to wait between improvement cycles. Default: ${DEFAULT-VALUE}")
    private int intervalMinutes;

    @Override
    public Integer call() throws Exception {
        if (!targetDir.isDirectory()) {
            System.err.println("Error: --target-dir does not exist or is not a directory: " + targetDir);
            return 1;
        }

        File workingDir;
        try {
            workingDir = targetDir.getCanonicalFile();
        } catch (Exception e) {
            workingDir = targetDir;
        }

        String model = parent.getModel();
        boolean dryRun = parent.isDryRun();

        if (dryRun) {
            System.out.println("=== DRY-RUN MODE ===");
        }

        System.out.println("Target directory: " + workingDir.getAbsolutePath());
        System.out.println("Improvement loop started. Press Ctrl+C to stop.\n");

        try (var client = new CopilotClient()) {
            client.start().get();
            System.out.println("Copilot SDK started.");

            int cycle = 0;
            while (!Thread.currentThread().isInterrupted()) {
                cycle++;
                System.out.println("\n========================================");
                System.out.println("  Improvement Cycle #" + cycle);
                System.out.println("========================================");

                int result = runImprovementCycle(client, model, dryRun, workingDir);

                if (result < 0) {
                    System.out.println("\nNo improvements found. Exiting.");
                    return 0;
                }

                System.out.println("\nNext cycle in " + intervalMinutes + " minute(s)...");
                try {
                    TimeUnit.MINUTES.sleep(intervalMinutes);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("\nInterrupted. Exiting.");
                    break;
                }
            }
        }

        return 0;
    }

    public int runImprovementCycle(CopilotClient client, String model, boolean dryRun,
                                    File workingDir) throws Exception {
        // Step 1: Codebase Analysis
        System.out.println("\n=== Step 1: Codebase Analysis ===");
        var analysisSession = new CodebaseAnalysisSession(client, model, workingDir, categories);
        var discoveries = analysisSession.run();
        System.out.println("Discovered " + discoveries.size() + " improvement opportunity(ies)");

        if (discoveries.isEmpty()) {
            System.out.println("No improvements found. Cycle complete.");
            return -1;
        }

        // Step 2: Prioritization
        System.out.println("\n=== Step 2: Prioritization ===");
        var prioritizationSession = new PrioritizationSession(client, model);
        var prioritized = prioritizationSession.run(discoveries, maxIssues);
        System.out.println("Selected " + prioritized.size() + " item(s) for implementation");

        if (prioritized.isEmpty()) {
            System.out.println("No items selected. Cycle complete.");
            return -1;
        }

        // Step 3: Implement & Verify
        System.out.println("\n=== Step 3: Implementing " + prioritized.size() + " improvement(s) ===");

        record ImplResult(String title, boolean success) {}
        var implResults = new ArrayList<ImplResult>();

        for (var item : prioritized) {
            System.out.println("\n--- Implementing: " + item.title() + " ---");
            try {
                var implSession = new LocalImplementationSession(client, model, workingDir, dryRun);
                implSession.run(item.title(), item.description());

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

                        String fixDescription = item.description() + "\n\n---\n\n"
                            + "# ⚠ Previous implementation has errors. Fix them:\n\n"
                            + errorContext;

                        var fixSession = new LocalImplementationSession(client, model, workingDir, dryRun);
                        fixSession.run(item.title(), fixDescription);
                    } catch (Exception e) {
                        System.err.println("  ⚠ Verification failed: " + e.getMessage());
                        break;
                    }
                }

                implResults.add(new ImplResult(item.title(), verified || dryRun));
            } catch (Exception e) {
                implResults.add(new ImplResult(item.title(), false));
                System.err.println("  ✗ Implementation failed: " + e.getMessage());
            }
        }

        // Summary
        long successCount = implResults.stream().filter(ImplResult::success).count();
        long failCount = implResults.stream().filter(r -> !r.success()).count();

        System.out.println("\n=== Cycle Summary ===");
        System.out.println("Discoveries:      " + discoveries.size());
        System.out.println("Prioritized:      " + prioritized.size());
        System.out.println("Implementations:  " + successCount + " succeeded, " + failCount + " failed");

        return 0;
    }
}
