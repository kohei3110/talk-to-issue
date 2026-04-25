package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ReadFileTool {

    private final File workingDir;

    public ReadFileTool(File workingDir) {
        this.workingDir = workingDir;
    }

    public ToolDefinition build() {
        return ToolDefinition.createSkipPermission(
            "read_file",
            "Read the contents of a file. The path should be relative to the working directory.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "path", Map.of("type", "string", "description", "Relative or absolute path to the file to read"),
                    "start_line", Map.of("type", "integer", "description", "Optional 1-based start line number"),
                    "end_line", Map.of("type", "integer", "description", "Optional 1-based end line number")
                ),
                "required", List.of("path")
            ),
            invocation -> CompletableFuture.supplyAsync(() -> {
                try {
                    var args = invocation.getArguments();
                    String filePath = (String) args.get("path");
                    Path resolved = resolveAndValidate(filePath);

                    List<String> lines = Files.readAllLines(resolved);

                    int start = args.containsKey("start_line")
                        ? Math.max(1, ((Number) args.get("start_line")).intValue()) : 1;
                    int end = args.containsKey("end_line")
                        ? Math.min(lines.size(), ((Number) args.get("end_line")).intValue()) : lines.size();

                    var selected = lines.subList(start - 1, end);
                    return Map.of(
                        "path", resolved.toString(),
                        "content", String.join("\n", selected),
                        "total_lines", lines.size(),
                        "showing", start + "-" + end
                    );
                } catch (Exception e) {
                    return Map.of("status", "error", "message", e.getMessage());
                }
            })
        );
    }

    private Path resolveAndValidate(String filePath) throws IOException {
        Path base = workingDir.toPath().toRealPath();
        Path target = base.resolve(filePath).normalize().toRealPath();
        if (!target.startsWith(base)) {
            throw new SecurityException("Path is outside the working directory: " + filePath);
        }
        return target;
    }
}
