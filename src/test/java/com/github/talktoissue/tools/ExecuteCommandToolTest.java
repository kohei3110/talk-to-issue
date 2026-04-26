package com.github.talktoissue.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ExecuteCommandToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void dryRunReturnsMessage() throws Exception {
        var result = invoke(true, Map.of("command", "echo hello"));

        assertEquals("dry_run", result.get("status"));
        assertTrue(result.get("message").toString().contains("echo hello"));
    }

    @Test
    void executesSimpleCommand() throws Exception {
        var result = invoke(false, Map.of("command", "echo hello"));

        assertEquals(0, result.get("exit_code"));
        assertTrue(result.get("stdout").toString().contains("hello"));
    }

    @Test
    void capturesNonZeroExitCode() throws Exception {
        var result = invoke(false, Map.of("command", "exit 1"));

        assertEquals(1, result.get("exit_code"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(boolean dryRun, Map<String, Object> args) throws Exception {
        var tool = new ExecuteCommandTool(tempDir.toFile(), dryRun);
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(30, TimeUnit.SECONDS);
    }
}
