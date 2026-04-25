package com.github.talktoissue.commands;

import com.github.copilot.sdk.CopilotClient;
import com.github.talktoissue.App;
import com.github.talktoissue.IntentDriftDetectorSession;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
    name = "drift",
    description = "Detect intent drift between a PR and its linked issue/meeting decisions."
)
public class DriftCommand implements Callable<Integer> {

    @ParentCommand
    private App parent;

    @Option(names = {"--pr"}, required = true,
            description = "Pull request number to analyze")
    private int prNumber;

    @Option(names = {"--issue"},
            description = "Linked issue number (auto-detected from PR body if omitted)")
    private Integer issueNumber;

    @Option(names = {"-f", "--file"},
            description = "Path to the meeting transcript file for additional context")
    private Path transcriptFile;

    @Override
    public Integer call() throws Exception {
        String token = System.getenv("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("Error: GITHUB_TOKEN environment variable is not set.");
            return 1;
        }

        if (transcriptFile != null && !Files.exists(transcriptFile)) {
            System.err.println("Error: Transcript file not found: " + transcriptFile);
            return 1;
        }

        File workingDir = parent.getWorkingDir();
        if (workingDir == null || !workingDir.isDirectory()) {
            System.err.println("Error: --working-dir is required for the drift command.");
            return 1;
        }

        String transcript = transcriptFile != null ? Files.readString(transcriptFile) : null;

        GitHub gitHub = new GitHubBuilder().withOAuthToken(token).build();
        var repository = gitHub.getRepository(parent.getRepoFullName());
        System.out.println("Connected to repository: " + repository.getFullName());

        try (var client = new CopilotClient()) {
            client.start().get();
            System.out.println("Copilot SDK started.");

            System.out.println("\n=== Analyzing PR #" + prNumber + " for intent drift ===");
            if (issueNumber != null) {
                System.out.println("Linked issue: #" + issueNumber);
            }
            if (transcript != null) {
                System.out.println("Meeting transcript provided (" + transcript.length() + " chars)");
            }

            var driftSession = new IntentDriftDetectorSession(client, parent.getModel(), repository, workingDir);
            var report = driftSession.run(prNumber, issueNumber, transcript);

            String icon = switch (report.verdict()) {
                case "pass" -> "✓";
                case "warn" -> "⚠";
                case "fail" -> "✗";
                default -> "?";
            };

            System.out.println("\n=== Drift Analysis: " + icon + " " + report.verdict().toUpperCase() + " ===");
            System.out.println(report.overallSummary());

            if (!report.drifts().isEmpty()) {
                System.out.println("\nDrifts detected:");
                for (var drift : report.drifts()) {
                    System.out.println("  [" + drift.severity().toUpperCase() + "] " + drift.type());
                    if (!drift.file().isEmpty()) {
                        System.out.println("    File: " + drift.file());
                    }
                    System.out.println("    " + drift.description());
                }
            }

            if (!report.coverageOfRequirements().isEmpty()) {
                System.out.println("\nRequirement coverage:");
                for (var cov : report.coverageOfRequirements()) {
                    String status = switch (cov.status()) {
                        case "met" -> "✓";
                        case "partial" -> "◐";
                        case "unmet" -> "✗";
                        default -> "?";
                    };
                    System.out.println("  " + status + " " + cov.requirement());
                    System.out.println("    " + cov.evidence());
                }
            }

            if (!report.suggestions().isEmpty()) {
                System.out.println("\nSuggestions:");
                for (var s : report.suggestions()) {
                    System.out.println("  → " + s);
                }
            }

            if ("fail".equals(report.verdict())) {
                return 1;
            }
        }

        return 0;
    }
}
