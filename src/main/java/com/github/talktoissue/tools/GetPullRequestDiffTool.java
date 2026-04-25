package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;
import org.kohsuke.github.GHRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GetPullRequestDiffTool {

    private final GHRepository repository;

    public GetPullRequestDiffTool(GHRepository repository) {
        this.repository = repository;
    }

    public ToolDefinition build() {
        return ToolDefinition.createSkipPermission(
            "get_pull_request_diff",
            "Fetch a pull request's metadata and file-level diffs. Returns PR title, body, branch info, and changed files with patches.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "pr_number", Map.of("type", "integer", "description", "The pull request number to fetch")
                ),
                "required", List.of("pr_number")
            ),
            invocation -> CompletableFuture.supplyAsync(() -> {
                try {
                    var args = invocation.getArguments();
                    int prNumber = ((Number) args.get("pr_number")).intValue();

                    var pr = repository.getPullRequest(prNumber);
                    var files = pr.listFiles().toList();

                    var changedFiles = files.stream()
                        .map(f -> Map.of(
                            "filename", (Object) f.getFilename(),
                            "status", f.getStatus(),
                            "additions", f.getAdditions(),
                            "deletions", f.getDeletions(),
                            "patch", f.getPatch() != null ? f.getPatch() : ""
                        ))
                        .toList();

                    return Map.of(
                        "number", pr.getNumber(),
                        "title", pr.getTitle(),
                        "body", pr.getBody() != null ? pr.getBody() : "",
                        "state", pr.getState().toString(),
                        "head", pr.getHead().getRef(),
                        "base", pr.getBase().getRef(),
                        "additions", pr.getAdditions(),
                        "deletions", pr.getDeletions(),
                        "changedFiles", changedFiles
                    );
                } catch (Exception e) {
                    return Map.of("status", "error", "message", e.getMessage());
                }
            })
        );
    }
}
