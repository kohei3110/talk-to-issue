package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ExecuteCommandTool {

    private final File workingDir;
    private final boolean dryRun;

    public ExecuteCommandTool(File workingDir, boolean dryRun) {
        this.workingDir = workingDir;
        this.dryRun = dryRun;
    }

    public ToolDefinition build() {
        return ToolDefinition.create(
            "execute_command",
            "Execute a shell command in the working directory. Returns stdout, stderr, and exit code. Use for running tests, build commands, etc.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "command", Map.of("type", "string", "description", "The shell command to execute")
                ),
                "required", List.of("command")
            ),
            invocation -> CompletableFuture.supplyAsync(() -> {
                try {
                    var args = invocation.getArguments();
                    String command = (String) args.get("command");

                    if (dryRun) {
                        return Map.of(
                            "status", "dry_run",
                            "message", "Would execute: " + command
                        );
                    }

                    ProcessBuilder pb = new ProcessBuilder("sh", "-c", command)
                        .directory(workingDir)
                        .redirectErrorStream(false);

                    Process process = pb.start();

                    StringBuilder stdout = new StringBuilder();
                    StringBuilder stderr = new StringBuilder();
                    int maxChars = 10000;

                    try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null && stdout.length() < maxChars) {
                            stdout.append(line).append("\n");
                        }
                    }

                    try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null && stderr.length() < maxChars) {
                            stderr.append(line).append("\n");
                        }
                    }

                    boolean finished = process.waitFor(60, TimeUnit.SECONDS);
                    if (!finished) {
                        process.destroyForcibly();
                        return Map.of("status", "error", "message", "Command timed out after 60 seconds");
                    }

                    return Map.of(
                        "exit_code", process.exitValue(),
                        "stdout", stdout.toString(),
                        "stderr", stderr.toString()
                    );
                } catch (Exception e) {
                    return Map.of("status", "error", "message", e.getMessage());
                }
            })
        );
    }
}
