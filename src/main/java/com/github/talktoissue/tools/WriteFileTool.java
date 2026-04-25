package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class WriteFileTool {

    private final File workingDir;
    private final boolean dryRun;

    public WriteFileTool(File workingDir, boolean dryRun) {
        this.workingDir = workingDir;
        this.dryRun = dryRun;
    }

    public ToolDefinition build() {
        return ToolDefinition.create(
            "write_file",
            "Write content to a file. Creates the file and parent directories if they don't exist. The path should be relative to the working directory.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "path", Map.of("type", "string", "description", "Relative path to the file to write"),
                    "content", Map.of("type", "string", "description", "The content to write to the file")
                ),
                "required", List.of("path", "content")
            ),
            invocation -> CompletableFuture.supplyAsync(() -> {
                try {
                    var args = invocation.getArguments();
                    String filePath = (String) args.get("path");
                    String content = (String) args.get("content");
                    Path resolved = resolveAndValidate(filePath);

                    if (dryRun) {
                        return Map.of(
                            "status", "dry_run",
                            "message", "Would write " + content.length() + " chars to " + filePath
                        );
                    }

                    Files.createDirectories(resolved.getParent());
                    Files.writeString(resolved, content);
                    return Map.of(
                        "status", "success",
                        "path", resolved.toString(),
                        "bytes_written", content.length()
                    );
                } catch (Exception e) {
                    return Map.of("status", "error", "message", e.getMessage());
                }
            })
        );
    }

    private Path resolveAndValidate(String filePath) throws IOException {
        Path base = workingDir.toPath().toRealPath();
        Path target = base.resolve(filePath).normalize();
        // For new files, check that the normalized path starts with base
        if (!target.startsWith(base)) {
            throw new SecurityException("Path is outside the working directory: " + filePath);
        }
        return target;
    }
}
