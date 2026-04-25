package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class SearchCodeTool {

    private final File workingDir;

    public SearchCodeTool(File workingDir) {
        this.workingDir = workingDir;
    }

    public ToolDefinition build() {
        return ToolDefinition.createSkipPermission(
            "search_code",
            "Search for a text pattern (regex) in files within the working directory. Returns matching lines with file paths and line numbers. Skips binary files and hidden directories.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "pattern", Map.of("type", "string", "description",
                        "Regex pattern to search for in file contents"),
                    "glob", Map.of("type", "string", "description",
                        "Optional glob pattern to filter files (e.g. '*.java', '*.md'). Defaults to all files.")
                ),
                "required", List.of("pattern")
            ),
            invocation -> CompletableFuture.supplyAsync(() -> {
                try {
                    var args = invocation.getArguments();
                    String patternStr = (String) args.get("pattern");
                    String glob = args.containsKey("glob") ? (String) args.get("glob") : null;
                    Pattern regex = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);

                    Path base = workingDir.toPath().toRealPath();
                    List<Map<String, Object>> matches = new ArrayList<>();
                    int maxMatches = 50;

                    Files.walkFileTree(base, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            String name = dir.getFileName().toString();
                            if (name.startsWith(".") || name.equals("node_modules") || name.equals("target")) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (matches.size() >= maxMatches) {
                                return FileVisitResult.TERMINATE;
                            }
                            String fileName = file.getFileName().toString();
                            if (glob != null && !matchGlob(fileName, glob)) {
                                return FileVisitResult.CONTINUE;
                            }
                            // Skip binary / large files
                            if (attrs.size() > 1_000_000) {
                                return FileVisitResult.CONTINUE;
                            }
                            try {
                                List<String> lines = Files.readAllLines(file);
                                for (int i = 0; i < lines.size() && matches.size() < maxMatches; i++) {
                                    if (regex.matcher(lines.get(i)).find()) {
                                        matches.add(Map.of(
                                            "file", base.relativize(file).toString(),
                                            "line", i + 1,
                                            "content", lines.get(i).trim()
                                        ));
                                    }
                                }
                            } catch (Exception ignored) {
                                // Skip files that can't be read as text
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });

                    return Map.of(
                        "matches", matches,
                        "total_matches", matches.size(),
                        "truncated", matches.size() >= maxMatches
                    );
                } catch (Exception e) {
                    return Map.of("status", "error", "message", e.getMessage());
                }
            })
        );
    }

    private static boolean matchGlob(String fileName, String glob) {
        String regex = glob.replace(".", "\\.").replace("*", ".*").replace("?", ".");
        return fileName.matches(regex);
    }
}
