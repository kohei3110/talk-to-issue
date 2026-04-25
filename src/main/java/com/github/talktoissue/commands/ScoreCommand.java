package com.github.talktoissue.commands;

import com.github.copilot.sdk.CopilotClient;
import com.github.talktoissue.App;
import com.github.talktoissue.IssueQualityScorerSession;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.util.concurrent.Callable;

@Command(
    name = "score",
    description = "Score a GitHub issue's quality for AI coding agent readiness (0-100)."
)
public class ScoreCommand implements Callable<Integer> {

    @ParentCommand
    private App parent;

    @Option(names = {"--issue"}, required = true,
            description = "GitHub issue number to score")
    private int issueNumber;

    @Option(names = {"--min-score"}, defaultValue = "0",
            description = "Minimum acceptable score (exit code 1 if below). Default: ${DEFAULT-VALUE}")
    private int minScore;

    @Override
    public Integer call() throws Exception {
        String token = System.getenv("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("Error: GITHUB_TOKEN environment variable is not set.");
            return 1;
        }

        File workingDir = parent.getWorkingDir();
        if (workingDir == null || !workingDir.isDirectory()) {
            System.err.println("Error: --working-dir is required for the score command.");
            return 1;
        }

        GitHub gitHub = new GitHubBuilder().withOAuthToken(token).build();
        var repository = gitHub.getRepository(parent.getRepoFullName());
        System.out.println("Connected to repository: " + repository.getFullName());

        try (var client = new CopilotClient()) {
            client.start().get();
            System.out.println("Copilot SDK started.");

            System.out.println("\n=== Scoring Issue #" + issueNumber + " ===");
            var scorerSession = new IssueQualityScorerSession(client, parent.getModel(), repository, workingDir);
            var score = scorerSession.run(issueNumber);

            System.out.println("\n=== Quality Score: " + score.overallScore() + "/100 ===");
            for (var dim : score.dimensions()) {
                String bar = "█".repeat(dim.score() / 5) + "░".repeat(20 - dim.score() / 5);
                System.out.println("  " + String.format("%-25s", dim.name()) + " " + bar + " " + dim.score());
            }

            if (!score.suggestions().isEmpty()) {
                System.out.println("\nSuggestions:");
                for (var s : score.suggestions()) {
                    System.out.println("  → " + s);
                }
            }

            if (minScore > 0 && score.overallScore() < minScore) {
                System.out.println("\n✗ Score " + score.overallScore() + " is below minimum threshold " + minScore);
                return 1;
            }

            if (score.overallScore() >= 70) {
                System.out.println("\n✓ Issue is coding-agent-ready.");
            } else {
                System.out.println("\n⚠ Issue needs improvement before passing to a coding agent.");
            }
        }

        return 0;
    }
}
