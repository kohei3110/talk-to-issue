package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches review comments from a pull request.
 * Returns both top-level review comments and inline code review comments.
 */
public class GetPullRequestReviewsTool {

    private final GHRepository repository;

    public GetPullRequestReviewsTool(GHRepository repository) {
        this.repository = repository;
    }

    public ToolDefinition build() {
        return ToolDefinition.createSkipPermission(
            "get_pull_request_reviews",
            "Fetch all review comments and review summaries for a pull request. "
                + "Returns reviewer feedback including inline code comments with file/line references.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "pr_number", Map.of("type", "integer", "description", "The pull request number")
                ),
                "required", List.of("pr_number")
            ),
            invocation -> CompletableFuture.supplyAsync(() -> {
                try {
                    var args = invocation.getArguments();
                    int prNumber = ((Number) args.get("pr_number")).intValue();

                    GHPullRequest pr = repository.getPullRequest(prNumber);

                    // Get top-level reviews (approve, request_changes, comment)
                    var reviews = pr.listReviews().toList().stream()
                        .map(r -> {
                            String user;
                            try {
                                user = r.getUser() != null ? r.getUser().getLogin() : "unknown";
                            } catch (Exception e) {
                                user = "unknown";
                            }
                            return Map.<String, Object>of(
                                "id", r.getId(),
                                "state", r.getState().toString(),
                                "body", r.getBody() != null ? r.getBody() : "",
                                "user", user
                            );
                        })
                        .toList();

                    // Get inline review comments
                    var comments = pr.listReviewComments().toList().stream()
                        .map(c -> {
                            try {
                                return Map.<String, Object>of(
                                    "id", c.getId(),
                                    "path", c.getPath(),
                                    "line", c.getLine(),
                                    "body", c.getBody(),
                                    "user", c.getUser() != null ? c.getUser().getLogin() : "unknown",
                                    "diff_hunk", c.getDiffHunk() != null ? c.getDiffHunk() : ""
                                );
                            } catch (Exception e) {
                                return Map.<String, Object>of(
                                    "id", c.getId(),
                                    "path", c.getPath(),
                                    "body", c.getBody(),
                                    "line", 0,
                                    "user", "unknown",
                                    "diff_hunk", ""
                                );
                            }
                        })
                        .toList();

                    return Map.of(
                        "pr_number", prNumber,
                        "reviews", reviews,
                        "inline_comments", comments,
                        "total_reviews", reviews.size(),
                        "total_comments", comments.size()
                    );
                } catch (Exception e) {
                    return Map.of("status", "error", "message", e.getMessage());
                }
            })
        );
    }
}
