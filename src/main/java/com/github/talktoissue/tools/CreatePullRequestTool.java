package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CreatePullRequestTool {

    private final GHRepository repository;
    private final boolean dryRun;

    public CreatePullRequestTool(GHRepository repository, boolean dryRun) {
        this.repository = repository;
        this.dryRun = dryRun;
    }

    public ToolDefinition build() {
        return ToolDefinition.create(
            "create_pull_request",
            "Create a pull request on GitHub from the current branch to main. Automatically links to the related issue by appending 'Closes #issue_number' to the body.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "title", Map.of("type", "string", "description", "Pull request title"),
                    "body", Map.of("type", "string", "description", "Pull request body in Markdown describing the changes"),
                    "issue_number", Map.of("type", "integer", "description", "The GitHub issue number this PR resolves")
                ),
                "required", List.of("title", "body", "issue_number")
            ),
            invocation -> CompletableFuture.supplyAsync(() -> {
                try {
                    var args = invocation.getArguments();
                    String title = (String) args.get("title");
                    String body = (String) args.get("body");
                    int issueNumber = ((Number) args.get("issue_number")).intValue();

                    String fullBody = body + "\n\nCloses #" + issueNumber;
                    String head = "issue-" + issueNumber;
                    String base = "main";

                    if (dryRun) {
                        System.out.println("[DRY-RUN] Would create PR: " + title);
                        System.out.println("  Head: " + head + " → Base: " + base);
                        System.out.println("  Body: " + fullBody.substring(0, Math.min(fullBody.length(), 100)) + "...");
                        return Map.of(
                            "status", "dry-run",
                            "title", title,
                            "head", head,
                            "base", base
                        );
                    }

                    GHPullRequest pr = repository.createPullRequest(title, head, base, fullBody);
                    System.out.println("Created PR #" + pr.getNumber() + ": " + pr.getTitle());

                    return Map.of(
                        "status", "created",
                        "number", pr.getNumber(),
                        "title", pr.getTitle(),
                        "url", pr.getHtmlUrl().toString()
                    );
                } catch (Exception e) {
                    return Map.of("status", "error", "message", e.getMessage());
                }
            })
        );
    }
}
