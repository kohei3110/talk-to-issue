package com.github.talktoissue.commands;

import com.github.copilot.sdk.CopilotClient;
import com.github.talktoissue.App;
import com.github.talktoissue.IssueCompilerSession;
import com.github.talktoissue.TranscriptFetchSession;
import com.github.talktoissue.tools.CreateIssueTool;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "compile",
    description = "Compile meeting transcript into coding-agent-ready GitHub issues with structured templates and codebase context."
)
public class CompileCommand implements Callable<Integer> {

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
            System.err.println("Error: --working-dir is required for the compile command.");
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

            System.out.println("\n=== Compiling transcript into coding-agent-ready issues ===");
            var compilerSession = new IssueCompilerSession(client, model, repository, workingDir, dryRun);
            List<CreateIssueTool.CreatedIssue> createdIssues = compilerSession.run(resolvedTranscript);

            System.out.println("\n=== Results ===");
            System.out.println("Created " + createdIssues.size() + " coding-agent-ready issue(s):");
            for (var issue : createdIssues) {
                System.out.println("  #" + issue.number() + ": " + issue.title());
                if (!dryRun) {
                    System.out.println("    " + issue.url());
                }
            }
        }

        return 0;
    }
}
