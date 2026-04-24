package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CreateBranchTool {

    private final File workingDir;

    public CreateBranchTool(File workingDir) {
        this.workingDir = workingDir;
    }

    public ToolDefinition build() {
        return ToolDefinition.create(
            "create_branch",
            "Create a new git branch for the given issue number and switch to it. Branch name follows the pattern 'issue-{number}'.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "issue_number", Map.of("type", "integer", "description", "The GitHub issue number to create a branch for")
                ),
                "required", List.of("issue_number")
            ),
            invocation -> CompletableFuture.supplyAsync(() -> {
                try {
                    var args = invocation.getArguments();
                    int issueNumber = ((Number) args.get("issue_number")).intValue();
                    String branchName = "issue-" + issueNumber;

                    var process = new ProcessBuilder("git", "checkout", "-b", branchName)
                        .directory(workingDir)
                        .redirectErrorStream(true)
                        .start();

                    String output = new String(process.getInputStream().readAllBytes());
                    int exitCode = process.waitFor();

                    if (exitCode != 0) {
                        return Map.of("status", "error", "message", output.trim());
                    }

                    System.out.println("Created branch: " + branchName);
                    return Map.of("status", "created", "branch", branchName);
                } catch (Exception e) {
                    return Map.of("status", "error", "message", e.getMessage());
                }
            })
        );
    }
}
