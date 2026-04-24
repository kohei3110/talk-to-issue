package com.github.talktoissue;

import com.github.copilot.sdk.CopilotClient;
import com.github.talktoissue.tools.CreateIssueTool;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "talk-to-issue",
    mixinStandardHelpOptions = true,
    version = "1.0",
    description = "Analyze meeting transcripts, create GitHub issues, implement solutions, and open PRs — all automatically."
)
public class App implements Callable<Integer> {

    @Option(names = {"-f", "--file"}, required = true,
            description = "Path to the meeting transcript file")
    private Path transcriptFile;

    @Option(names = {"-r", "--repo"}, required = true,
            description = "Target GitHub repository (owner/repo format)")
    private String repoFullName;

    @Option(names = {"-w", "--working-dir"}, required = true,
            description = "Path to the local clone of the target repository")
    private File workingDir;

    @Option(names = {"-m", "--model"}, defaultValue = "gpt-4.1",
            description = "LLM model to use (default: ${DEFAULT-VALUE})")
    private String model;

    @Option(names = {"--dry-run"}, defaultValue = "false",
            description = "Simulate without creating issues or PRs")
    private boolean dryRun;

    @Option(names = {"--health-port"}, defaultValue = "8080",
        description = "Port for the health check HTTP server (default: 8080)")
    private int healthPort;

    @Override
    public Integer call() throws Exception {
        // Validate inputs
        if (!Files.exists(transcriptFile)) {
            System.err.println("Error: Transcript file not found: " + transcriptFile);
            return 1;
        }
        if (!workingDir.isDirectory()) {
            System.err.println("Error: Working directory not found: " + workingDir);
            return 1;
        }

        String token = System.getenv("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("Error: GITHUB_TOKEN environment variable is not set.");
            System.err.println("Set it with: export GITHUB_TOKEN=ghp_your_token_here");
            return 1;
        }

        // Read transcript
        String transcript = Files.readString(transcriptFile);
        System.out.println("Loaded transcript: " + transcriptFile + " (" + transcript.length() + " chars)");

        // Connect to GitHub
        GitHub gitHub = new GitHubBuilder().withOAuthToken(token).build();
        var repository = gitHub.getRepository(repoFullName);
        System.out.println("Connected to repository: " + repository.getFullName());

        if (dryRun) {
            System.out.println("=== DRY-RUN MODE ===");
        }

        try (var client = new CopilotClient()) {
            client.start().get();
            System.out.println("Copilot SDK started.");

            // Phase 1: Create issues from transcript
            System.out.println("\n=== Phase 1: Analyzing transcript and creating issues ===");
            var issueSession = new IssueCreationSession(client, model, repository, dryRun);
            List<CreateIssueTool.CreatedIssue> createdIssues = issueSession.run(transcript);

            System.out.println("\n--- Created " + createdIssues.size() + " issue(s) ---");
            for (var issue : createdIssues) {
                System.out.println("  #" + issue.number() + ": " + issue.title());
            }

            if (createdIssues.isEmpty()) {
                System.out.println("No issues created. Nothing to implement.");
                return 0;
            }

            // Phase 2: Implement each issue and create PRs
            System.out.println("\n=== Phase 2: Implementing issues and creating PRs ===");
            int successCount = 0;
            int failCount = 0;

            for (var issue : createdIssues) {
                System.out.println("\n--- Implementing Issue #" + issue.number() + ": " + issue.title() + " ---");

                try {
                    // Reset to main branch before each implementation
                    resetToMain();

                    // Fetch issue body from GitHub (or use dry-run placeholder)
                    String issueBody;
                    if (dryRun) {
                        issueBody = "Dry-run issue body for: " + issue.title();
                    } else {
                        var ghIssue = repository.getIssue(issue.number());
                        issueBody = ghIssue.getBody() != null ? ghIssue.getBody() : issue.title();
                    }

                    var implSession = new ImplementationSession(client, model, repository, workingDir, dryRun);
                    implSession.run(issue.number(), issue.title(), issueBody);

                    successCount++;
                    System.out.println("  ✓ Issue #" + issue.number() + " implemented successfully.");
                } catch (Exception e) {
                    failCount++;
                    System.err.println("  ✗ Failed to implement Issue #" + issue.number() + ": " + e.getMessage());
                }
            }

            // Summary
            System.out.println("\n=== Summary ===");
            System.out.println("Issues created: " + createdIssues.size());
            System.out.println("Implementations succeeded: " + successCount);
            System.out.println("Implementations failed: " + failCount);
        }

        return 0;
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

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }
}
