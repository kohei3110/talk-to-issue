package com.github.talktoissue.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReadFileToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void readsEntireFile() throws Exception {
        Files.writeString(tempDir.resolve("test.txt"), "line1\nline2\nline3");

        var result = invoke(Map.of("path", "test.txt"));

        assertEquals("line1\nline2\nline3", result.get("content"));
        assertEquals(3, result.get("total_lines"));
        assertEquals("1-3", result.get("showing"));
    }

    @Test
    void readsWithLineRange() throws Exception {
        Files.writeString(tempDir.resolve("lines.txt"), "a\nb\nc\nd\ne");

        var result = invoke(Map.of("path", "lines.txt", "start_line", 2, "end_line", 4));

        assertEquals("b\nc\nd", result.get("content"));
        assertEquals(5, result.get("total_lines"));
        assertEquals("2-4", result.get("showing"));
    }

    @Test
    void pathTraversalBlocked() throws Exception {
        var result = invoke(Map.of("path", "../outside.txt"));
        assertEquals("error", result.get("status"));
    }

    @Test
    void nonExistentFileReturnsError() throws Exception {
        var result = invoke(Map.of("path", "missing.txt"));
        assertEquals("error", result.get("status"));
    }

    @Test
    void readsSubdirectoryFile() throws Exception {
        Path subDir = tempDir.resolve("sub/dir");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("file.txt"), "nested content");

        var result = invoke(Map.of("path", "sub/dir/file.txt"));
        assertEquals("nested content", result.get("content"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(Map<String, Object> args) throws Exception {
        var tool = new ReadFileTool(tempDir.toFile());
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
