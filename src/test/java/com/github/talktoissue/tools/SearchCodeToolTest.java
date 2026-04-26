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

class SearchCodeToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void findsMatchingLines() throws Exception {
        Files.writeString(tempDir.resolve("Main.java"), "public class Main {\n  int foo = 42;\n}");

        var result = invoke(Map.of("pattern", "foo"));

        @SuppressWarnings("unchecked")
        var matches = (List<Map<String, Object>>) result.get("matches");
        assertEquals(1, matches.size());
        assertTrue(matches.get(0).get("content").toString().contains("foo"));
        assertEquals(2, matches.get(0).get("line"));
    }

    @Test
    void noMatchesReturnsEmpty() throws Exception {
        Files.writeString(tempDir.resolve("test.txt"), "hello world");

        var result = invoke(Map.of("pattern", "zzzzz"));

        assertEquals(0, result.get("total_matches"));
    }

    @Test
    void globFilterWorks() throws Exception {
        Files.writeString(tempDir.resolve("code.java"), "hello java");
        Files.writeString(tempDir.resolve("readme.md"), "hello markdown");

        var result = invoke(Map.of("pattern", "hello", "glob", "*.java"));

        @SuppressWarnings("unchecked")
        var matches = (List<Map<String, Object>>) result.get("matches");
        assertEquals(1, matches.size());
        assertTrue(matches.get(0).get("file").toString().endsWith(".java"));
    }

    @Test
    void skipsHiddenDirectories() throws Exception {
        Path hidden = tempDir.resolve(".hidden");
        Files.createDirectory(hidden);
        Files.writeString(hidden.resolve("secret.txt"), "findme");
        Files.writeString(tempDir.resolve("visible.txt"), "findme");

        var result = invoke(Map.of("pattern", "findme"));

        @SuppressWarnings("unchecked")
        var matches = (List<Map<String, Object>>) result.get("matches");
        assertEquals(1, matches.size());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(Map<String, Object> args) throws Exception {
        var tool = new SearchCodeTool(tempDir.toFile());
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
