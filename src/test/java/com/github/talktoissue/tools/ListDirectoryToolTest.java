package com.github.talktoissue.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ListDirectoryToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void listsRootDirectory() throws Exception {
        Files.writeString(tempDir.resolve("file1.txt"), "a");
        Files.writeString(tempDir.resolve("file2.txt"), "b");
        Files.createDirectory(tempDir.resolve("subdir"));

        var result = invoke(Map.of("path", "."));

        @SuppressWarnings("unchecked")
        var entries = (List<String>) result.get("entries");
        assertTrue(entries.contains("file1.txt"));
        assertTrue(entries.contains("file2.txt"));
        assertTrue(entries.contains("subdir/"));
        assertEquals(3, result.get("count"));
    }

    @Test
    void listsSubdirectory() throws Exception {
        Files.createDirectory(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub/inner.txt"), "content");

        var result = invoke(Map.of("path", "sub"));

        @SuppressWarnings("unchecked")
        var entries = (List<String>) result.get("entries");
        assertTrue(entries.contains("inner.txt"));
    }

    @Test
    void pathTraversalBlocked() throws Exception {
        var result = invoke(Map.of("path", "../../"));
        assertEquals("error", result.get("status"));
    }

    @Test
    void nonExistentDirectoryReturnsError() throws Exception {
        var result = invoke(Map.of("path", "nonexistent"));
        assertEquals("error", result.get("status"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(Map<String, Object> args) throws Exception {
        var tool = new ListDirectoryTool(tempDir.toFile());
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
