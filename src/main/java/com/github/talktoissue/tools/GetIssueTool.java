package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;
import org.kohsuke.github.GHRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GetIssueTool {

    private final GHRepository repository;

    public GetIssueTool(GHRepository repository) {
        this.repository = repository;
    }

    public ToolDefinition build() {
        return ToolDefinition.createSkipPermission(
            "get_issue",
            "Fetch a GitHub issue by number, including title, body, labels, and assignees. Use this to retrieve issue details for analysis.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "issue_number", Map.of("type", "integer", "description", "The GitHub issue number to fetch")
                ),
                "required", List.of("issue_number")
            ),
            invocation -> CompletableFuture.supplyAsync(() -> {
                try {
                    var args = invocation.getArguments();
                    int issueNumber = ((Number) args.get("issue_number")).intValue();

                    var issue = repository.getIssue(issueNumber);
                    return Map.of(
                        "number", issue.getNumber(),
                        "title", issue.getTitle(),
                        "body", issue.getBody() != null ? issue.getBody() : "",
                        "state", issue.getState().toString(),
                        "labels", issue.getLabels().stream()
                            .map(label -> label.getName()).toList(),
                        "assignees", issue.getAssignees().stream()
                            .map(user -> user.getLogin()).toList(),
                        "url", issue.getHtmlUrl().toString()
                    );
                } catch (Exception e) {
                    return Map.of("status", "error", "message", e.getMessage());
                }
            })
        );
    }
}
