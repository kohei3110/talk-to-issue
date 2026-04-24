package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class CreateIssueTool {

    public record CreatedIssue(int number, String title, String url) {}

    private final GHRepository repository;
    private final boolean dryRun;
    private final List<CreatedIssue> createdIssues = new CopyOnWriteArrayList<>();

    public CreateIssueTool(GHRepository repository, boolean dryRun) {
        this.repository = repository;
        this.dryRun = dryRun;
    }

    public List<CreatedIssue> getCreatedIssues() {
        return List.copyOf(createdIssues);
    }

    public ToolDefinition build() {
        return ToolDefinition.create(
            "create_issue",
            "Create a new GitHub issue in the target repository. Use this to file action items, tasks, or decisions extracted from the meeting transcript.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "title", Map.of("type", "string", "description", "Issue title — concise summary of the action item or task"),
                    "body", Map.of("type", "string", "description", "Issue body in Markdown. Include context: who is responsible, what needs to be done, deadline if mentioned, and relevant discussion from the transcript"),
                    "labels", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Labels to apply (must exist in the repository)"),
                    "assignees", Map.of("type", "array", "items", Map.of("type", "string"), "description", "GitHub usernames to assign")
                ),
                "required", List.of("title", "body")
            ),
            invocation -> CompletableFuture.supplyAsync(() -> {
                try {
                    var args = invocation.getArguments();
                    String title = (String) args.get("title");
                    String body = (String) args.get("body");
                    @SuppressWarnings("unchecked")
                    List<String> labels = (List<String>) args.getOrDefault("labels", List.of());
                    @SuppressWarnings("unchecked")
                    List<String> assignees = (List<String>) args.getOrDefault("assignees", List.of());

                    if (dryRun) {
                        System.out.println("[DRY-RUN] Would create issue: " + title);
                        System.out.println("  Body: " + body.substring(0, Math.min(body.length(), 100)) + "...");
                        System.out.println("  Labels: " + labels);
                        System.out.println("  Assignees: " + assignees);
                        int fakeNumber = createdIssues.size() + 1;
                        createdIssues.add(new CreatedIssue(fakeNumber, title, "https://github.com/dry-run/issues/" + fakeNumber));
                        return Map.of(
                            "status", "dry-run",
                            "number", fakeNumber,
                            "title", title
                        );
                    }

                    var builder = repository.createIssue(title).body(body);
                    for (String label : labels) {
                        builder.label(label);
                    }
                    for (String assignee : assignees) {
                        builder.assignee(assignee);
                    }
                    GHIssue issue = builder.create();

                    createdIssues.add(new CreatedIssue(issue.getNumber(), issue.getTitle(), issue.getHtmlUrl().toString()));
                    System.out.println("Created issue #" + issue.getNumber() + ": " + issue.getTitle());

                    return Map.of(
                        "status", "created",
                        "number", issue.getNumber(),
                        "title", issue.getTitle(),
                        "url", issue.getHtmlUrl().toString()
                    );
                } catch (Exception e) {
                    return Map.of("status", "error", "message", e.getMessage());
                }
            })
        );
    }
}
