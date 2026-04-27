package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReportDiscoveryToolTest {
    @Test
    void testGetDiscoveriesInitiallyEmpty() {
        ReportDiscoveryTool tool = new ReportDiscoveryTool();
        assertTrue(tool.getDiscoveries().isEmpty());
    }

    @Test
    void testBuildAndInvoke() throws Exception {
        ReportDiscoveryTool tool = new ReportDiscoveryTool();
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().valueToTree(Map.of(
            "discoveries", List.of(Map.of(
                "category", "test_gap",
                "title", "Add tests",
                "description", "Add missing tests",
                "severity", "medium",
                "affected_files", List.of("SomeFile.java"),
                "estimated_effort", "small"
            ))
        )));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
        assertEquals("received", result.get("status"));
        assertEquals(1, result.get("count"));
        assertEquals(1, tool.getDiscoveries().size());
    }
}
