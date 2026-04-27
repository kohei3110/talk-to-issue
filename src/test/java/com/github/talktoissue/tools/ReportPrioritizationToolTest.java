package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReportPrioritizationToolTest {
    @Test
    void testGetSelectedItemsInitiallyEmpty() {
        ReportPrioritizationTool tool = new ReportPrioritizationTool();
        assertTrue(tool.getSelectedItems().isEmpty());
    }

    @Test
    void testBuildAndInvoke() throws Exception {
        ReportPrioritizationTool tool = new ReportPrioritizationTool();
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().valueToTree(Map.of(
            "selected_items", List.of(Map.of(
                "title", "Prioritize this",
                "description", "Should be prioritized",
                "category", "test_gap",
                "priority_rank", 1,
                "rationale", "High impact"
            ))
        )));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
        assertEquals("received", result.get("status"));
        assertEquals(1, result.get("count"));
        assertEquals(1, tool.getSelectedItems().size());
    }
}
