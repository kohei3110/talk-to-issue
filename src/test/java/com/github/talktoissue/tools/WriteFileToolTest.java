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

class WriteFileToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void writesNewFile() throws Exception {
        var result = invoke(false, Map.of("path", "new.txt", "content", "hello"));

        assertEquals("success", result.get("status"));
        assertEquals("hello", Files.readString(tempDir.resolve("new.txt")));
    }

    @Test
    void overwritesExistingFile() throws Exception {
        Files.writeString(tempDir.resolve("exist.txt"), "old");

        var result = invoke(false, Map.of("path", "exist.txt", "content", "new"));

        assertEquals("success", result.get("status"));
        assertEquals("new", Files.readString(tempDir.resolve("exist.txt")));
    }

    @Test
    void createsParentDirectories() throws Exception {
        var result = invoke(false, Map.of("path", "a/b/c/deep.txt", "content", "deep"));

        assertEquals("success", result.get("status"));
        assertEquals("deep", Files.readString(tempDir.resolve("a/b/c/deep.txt")));
    }

    @Test
    void dryRunDoesNotCreateFile() throws Exception {
        var result = invoke(true, Map.of("path", "dry.txt", "content", "nope"));

        assertEquals("dry_run", result.get("status"));
        assertFalse(Files.exists(tempDir.resolve("dry.txt")));
    }

    @Test
    void pathTraversalBlocked() throws Exception {
        var result = invoke(false, Map.of("path", "../escape.txt", "content", "bad"));
        assertEquals("error", result.get("status"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(boolean dryRun, Map<String, Object> args) throws Exception {
        var tool = new WriteFileTool(tempDir.toFile(), dryRun);
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
