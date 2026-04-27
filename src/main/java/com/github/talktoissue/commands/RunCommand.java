package com.github.talktoissue.commands;

import com.github.copilot.sdk.CopilotClient;
import com.github.talktoissue.IssueCreationSession;
import com.github.talktoissue.ImplementationSession;
import com.github.talktoissue.TranscriptFetchSession;
import com.github.talktoissue.tools.CreateIssueTool;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import com.github.talktoissue.App;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "run",
    description = "Full workflow: analyze transcript → create issues → implement → commit to main."
)
public class RunCommand implements Callable<Integer> {

    @ParentCommand
    private App parent;

    @Option(names = {"-f", "--file"},
            description = "Path to the meeting transcript file")
    private Path transcriptFile;

    @Option(names = {"-q", "--workiq-query"},
            description = "Natural language query to fetch meeting transcript from Work IQ")
    private String workiqQuery;

    @Option(names = {"--tenant-id"},
            description = "Microsoft Entra tenant ID for Work IQ authentication")
    private String tenantId;

    @Override
    public Integer call() throws Exception {
        if (transcriptFile == null && workiqQuery == null) {
            System.err.println("Error: Either --file or --workiq-query must be specified.");
            return 1;
        }
        if (transcriptFile != null && workiqQuery != null) {
            System.err.println("Error: --file and --workiq-query are mutually exclusive.");
            return 1;
        }
        if (transcriptFile != null && !Files.exists(transcriptFile)) {
            System.err.println("Error: Transcript file not found: " + transcriptFile);
            return 1;
        }

        String token = System.getenv("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("Error: GITHUB_TOKEN environment variable is not set.");
            return 1;
        }

        File workingDir = parent.getWorkingDir();
        if (workingDir == null || !workingDir.isDirectory()) {
            System.err.println("Error: --working-dir is required for the run command.");
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

            String resolvedTranscript;
            if (transcript != null) {
                resolvedTranscript = transcript;
            } else {
                System.out.println("\n=== Fetching transcript from Work IQ ===");
                var fetchSession = new TranscriptFetchSession(client, model, tenantId);
                resolvedTranscript = fetchSession.run(workiqQuery);
                System.out.println("Transcript fetched (" + resolvedTranscript.length() + " chars)");
            }

            System.out.println("\n=== Phase 1: Analyzing transcript and creating issues ===");
            var issueSession = new IssueCreationSession(client, model, repository, dryRun);
            List<CreateIssueTool.CreatedIssue> createdIssues = issueSession.run(resolvedTranscript);

            System.out.println("\n--- Created " + createdIssues.size() + " issue(s) ---");
            for (var issue : createdIssues) {
                System.out.println("  #" + issue.number() + ": " + issue.title());
            }

            if (createdIssues.isEmpty()) {
                System.out.println("No issues created. Nothing to implement.");
                return 0;
            }

            System.out.println("\n=== Phase 2: Implementing issues ===" );
            int successCount = 0;
            int failCount = 0;

            for (var issue : createdIssues) {
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

                    successCount++;
                    System.out.println("  ✓ Issue #" + issue.number() + " implemented successfully.");
                } catch (Exception e) {
                    failCount++;
                    System.err.println("  ✗ Failed to implement Issue #" + issue.number() + ": " + e.getMessage());
                }
            }

            System.out.println("\n=== Summary ===");
            System.out.println("Issues created: " + createdIssues.size());
            System.out.println("Implementations succeeded: " + successCount);
            System.out.println("Implementations failed: " + failCount);
        }

        return 0;
    }
}
