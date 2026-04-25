package com.github.talktoissue.commands;

import com.github.copilot.sdk.CopilotClient;
import com.github.talktoissue.App;
import com.github.talktoissue.IssueCompilerSession;
import com.github.talktoissue.TranscriptFetchSession;
import com.github.talktoissue.context.ContextAggregator;
import com.github.talktoissue.context.ContextConfig;
import com.github.talktoissue.context.ContextSource;
import com.github.talktoissue.context.FileContextSource;
import com.github.talktoissue.context.WorkIQContextSource;
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
    name = "compile",
    description = "Compile context (transcripts, docs, issues, etc.) into coding-agent-ready GitHub issues."
)
public class CompileCommand implements Callable<Integer> {

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

    @Option(names = {"--context-config"},
            description = "Path to a YAML file defining multiple context sources")
    private Path contextConfig;

    @Option(names = {"--context"}, split = ",",
            description = "Inline context source(s): file:<path>, workiq:<query>, github:issues, github:prs")
    private List<String> contextSpecs;

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
            System.err.println("Error: --working-dir is required for the compile command.");
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

            String resolvedContext;

            if (hasNew) {
                // New context system
                List<ContextSource> sources;
                if (contextConfig != null) {
                    sources = ContextConfig.load(contextConfig, client, model, repository);
                } else {
                    sources = parseInlineContextSpecs(contextSpecs, client, model, repository);
                }
                System.out.println("\n=== Aggregating " + sources.size() + " context source(s) ===");
                resolvedContext = new ContextAggregator(sources).aggregate();
            } else if (transcriptFile != null) {
                resolvedContext = Files.readString(transcriptFile);
            } else {
                System.out.println("\n=== Fetching context from Work IQ ===");
                var fetchSession = new TranscriptFetchSession(client, model, tenantId);
                resolvedContext = fetchSession.run(workiqQuery);
                System.out.println("Context fetched (" + resolvedContext.length() + " chars)");
            }

            System.out.println("\n=== Compiling context into coding-agent-ready issues ===");
            var compilerSession = new IssueCompilerSession(client, model, repository, workingDir, dryRun);
            List<CreateIssueTool.CreatedIssue> createdIssues = compilerSession.run(resolvedContext);

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

    static List<ContextSource> parseInlineContextSpecs(List<String> specs, CopilotClient client,
                                                        String model,
                                                        org.kohsuke.github.GHRepository repository) {
        var sources = new ArrayList<ContextSource>();
        for (var spec : specs) {
            if (spec.startsWith("file:")) {
                sources.add(new FileContextSource(Path.of(spec.substring(5))));
            } else if (spec.startsWith("workiq:")) {
                sources.add(new WorkIQContextSource(spec.substring(7), null));
            } else if (spec.equals("github:issues")) {
                sources.add(new com.github.talktoissue.context.GitHubContextSource(
                    repository, com.github.talktoissue.context.GitHubContextSource.Scope.ISSUES, null));
            } else if (spec.equals("github:prs")) {
                sources.add(new com.github.talktoissue.context.GitHubContextSource(
                    repository, com.github.talktoissue.context.GitHubContextSource.Scope.PULL_REQUESTS, null));
            } else {
                throw new IllegalArgumentException("Unknown context spec: " + spec
                    + ". Supported: file:<path>, workiq:<query>, github:issues, github:prs");
            }
        }
        return sources;
    }
}
