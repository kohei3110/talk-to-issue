package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ListDirectoryTool {

    private final File workingDir;

    public ListDirectoryTool(File workingDir) {
        this.workingDir = workingDir;
    }

    public ToolDefinition build() {
        return ToolDefinition.createSkipPermission(
            "list_dir",
            "List the contents of a directory. Returns file names with '/' suffix for directories. The path should be relative to the working directory. Use '.' or omit path for the root.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "path", Map.of("type", "string", "description",
                        "Relative path to the directory to list. Defaults to '.' (working directory root).")
                ),
                "required", List.of()
            ),
            invocation -> CompletableFuture.supplyAsync(() -> {
                try {
                    var args = invocation.getArguments();
                    String dirPath = args.containsKey("path") ? (String) args.get("path") : ".";
                    Path resolved = resolveAndValidate(dirPath);

                    List<String> entries = new ArrayList<>();
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(resolved)) {
                        for (Path entry : stream) {
                            String name = entry.getFileName().toString();
                            if (Files.isDirectory(entry)) {
                                entries.add(name + "/");
                            } else {
                                entries.add(name);
                            }
                        }
                    }
                    entries.sort(String::compareTo);
                    return Map.of(
                        "path", resolved.toString(),
                        "entries", entries,
                        "count", entries.size()
                    );
                } catch (Exception e) {
                    return Map.of("status", "error", "message", e.getMessage());
                }
            })
        );
    }

    private Path resolveAndValidate(String dirPath) throws IOException {
        Path base = workingDir.toPath().toRealPath();
        Path target = base.resolve(dirPath).normalize().toRealPath();
        if (!target.startsWith(base)) {
            throw new SecurityException("Path is outside the working directory: " + dirPath);
        }
        return target;
    }
}
