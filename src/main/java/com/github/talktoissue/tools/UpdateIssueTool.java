package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class UpdateIssueTool {

    private final GHRepository repository;
    private final boolean dryRun;

    public UpdateIssueTool(GHRepository repository, boolean dryRun) {
        this.repository = repository;
        this.dryRun = dryRun;
    }

    public ToolDefinition build() {
        return ToolDefinition.create(
            "update_issue",
            "Update an existing GitHub issue's body. Use this to refine issue content with additional technical context, "
                + "more specific acceptance criteria, or improved clarity.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "issue_number", Map.of("type", "integer", "description", "The GitHub issue number to update"),
                    "body", Map.of("type", "string", "description", "The new issue body in Markdown. This replaces the entire body.")
                ),
                "required", List.of("issue_number", "body")
            ),
            invocation -> CompletableFuture.supplyAsync(() -> {
                try {
                    var args = invocation.getArguments();
                    int issueNumber = ((Number) args.get("issue_number")).intValue();
                    String body = (String) args.get("body");

                    if (dryRun) {
                        System.out.println("[DRY-RUN] Would update issue #" + issueNumber);
                        System.out.println("  New body: " + body.substring(0, Math.min(body.length(), 200)) + "...");
                        return Map.of(
                            "status", "dry_run",
                            "message", "Would update issue #" + issueNumber
                        );
                    }

                    GHIssue issue = repository.getIssue(issueNumber);
                    issue.setBody(body);

                    return Map.of(
                        "status", "updated",
                        "issue_number", issueNumber,
                        "url", issue.getHtmlUrl().toString()
                    );
                } catch (Exception e) {
                    return Map.of("status", "error", "message", e.getMessage());
                }
            })
        );
    }
}
