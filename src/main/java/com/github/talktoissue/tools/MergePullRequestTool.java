package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Merges a pull request via the GitHub API using squash merge.
 */
public class MergePullRequestTool {

    private final GHRepository repository;
    private final boolean dryRun;

    public MergePullRequestTool(GHRepository repository, boolean dryRun) {
        this.repository = repository;
        this.dryRun = dryRun;
    }

    public ToolDefinition build() {
        return ToolDefinition.create(
            "merge_pull_request",
            "Merge a pull request using squash merge. Only call this after all reviews are approved and checks pass.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "pr_number", Map.of("type", "integer", "description", "The pull request number to merge"),
                    "commit_message", Map.of("type", "string", "description", "The squash merge commit message")
                ),
                "required", List.of("pr_number", "commit_message")
            ),
            invocation -> CompletableFuture.supplyAsync(() -> {
                try {
                    var args = invocation.getArguments();
                    int prNumber = ((Number) args.get("pr_number")).intValue();
                    String commitMessage = (String) args.get("commit_message");

                    if (dryRun) {
                        System.out.println("[DRY-RUN] Would merge PR #" + prNumber + " with message: " + commitMessage);
                        return Map.of(
                            "status", "dry-run",
                            "pr_number", prNumber
                        );
                    }

                    GHPullRequest pr = repository.getPullRequest(prNumber);

                    if (!pr.getMergeable()) {
                        return Map.of(
                            "status", "error",
                            "message", "PR #" + prNumber + " is not mergeable (conflicts or checks failing)"
                        );
                    }

                    pr.merge(commitMessage, pr.getHead().getSha(), GHPullRequest.MergeMethod.SQUASH);
                    System.out.println("Merged PR #" + prNumber + " via squash merge");

                    return Map.of(
                        "status", "merged",
                        "pr_number", prNumber,
                        "merge_method", "squash"
                    );
                } catch (Exception e) {
                    return Map.of("status", "error", "message", e.getMessage());
                }
            })
        );
    }
}
