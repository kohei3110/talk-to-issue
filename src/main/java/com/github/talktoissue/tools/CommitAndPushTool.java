package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CommitAndPushTool {

    private final File workingDir;
    private final boolean dryRun;

    public CommitAndPushTool(File workingDir, boolean dryRun) {
        this.workingDir = workingDir;
        this.dryRun = dryRun;
    }

    public ToolDefinition build() {
        return ToolDefinition.create(
            "commit_and_push",
            "Stage all changes, commit with the given message, and push to the remote origin. Call this after making code changes to persist them.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "message", Map.of("type", "string", "description", "Git commit message describing the changes")
                ),
                "required", List.of("message")
            ),
            invocation -> CompletableFuture.supplyAsync(() -> {
                try {
                    var args = invocation.getArguments();
                    String message = (String) args.get("message");

                    if (dryRun) {
                        System.out.println("[DRY-RUN] Would commit and push: " + message);
                        return Map.of("status", "dry-run", "message", message);
                    }

                    // git add -A
                    int exitCode = runGit("add", "-A");
                    if (exitCode != 0) {
                        return Map.of("status", "error", "message", "git add failed");
                    }

                    // git commit -m "message"
                    exitCode = runGit("commit", "-m", message);
                    if (exitCode != 0) {
                        return Map.of("status", "error", "message", "git commit failed");
                    }

                    // git push -u origin HEAD
                    exitCode = runGit("push", "-u", "origin", "HEAD");
                    if (exitCode != 0) {
                        return Map.of("status", "error", "message", "git push failed");
                    }

                    System.out.println("Committed and pushed: " + message);
                    return Map.of("status", "pushed", "message", message);
                } catch (Exception e) {
                    return Map.of("status", "error", "message", e.getMessage());
                }
            })
        );
    }

    private int runGit(String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        var process = new ProcessBuilder(command)
            .directory(workingDir)
            .redirectErrorStream(true)
            .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println("git " + String.join(" ", args) + " failed: " + output.trim());
        }
        return exitCode;
    }
}
