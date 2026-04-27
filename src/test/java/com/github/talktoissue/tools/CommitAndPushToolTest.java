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

class CommitAndPushToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void dryRunReturnsMessage() throws Exception {
        var result = invoke(true, Map.of("message", "test commit"));

        assertEquals("dry-run", result.get("status"));
        assertEquals("test commit", result.get("message"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(boolean dryRun, Map<String, Object> args) throws Exception {
        var tool = new CommitAndPushTool(tempDir.toFile(), dryRun);
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
