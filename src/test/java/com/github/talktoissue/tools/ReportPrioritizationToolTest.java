package com.github.talktoissue.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReportPrioritizationToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void toolNameIsCorrect() {
        var tool = new ReportPrioritizationTool();
        assertEquals("report_prioritization", tool.build().name());
    }

    @Test
    void initialStateIsEmpty() {
        var tool = new ReportPrioritizationTool();
        assertTrue(tool.getSelectedItems().isEmpty());
    }

    @Test
    void capturesSingleItem() throws Exception {
        var tool = new ReportPrioritizationTool();
        var result = invoke(tool, Map.of(
            "selected_items", List.of(
                Map.of(
                    "title", "Fix security issue",
                    "description", "Input validation missing",
                    "category", "security",
                    "priority_rank", 1,
                    "rationale", "High impact, low effort"
                )
            )
        ));

        assertEquals("received", result.get("status"));
        assertEquals(1, result.get("count"));

        var items = tool.getSelectedItems();
        assertEquals(1, items.size());
        assertEquals("Fix security issue", items.get(0).title());
        assertEquals("security", items.get(0).category());
        assertEquals(1, items.get(0).priorityRank());
        assertEquals("High impact, low effort", items.get(0).rationale());
    }

    @Test
    void capturesMultipleItems() throws Exception {
        var tool = new ReportPrioritizationTool();
        invoke(tool, Map.of(
            "selected_items", List.of(
                Map.of("title", "First", "description", "d1", "category", "security",
                    "priority_rank", 1, "rationale", "r1"),
                Map.of("title", "Second", "description", "d2", "category", "test_gap",
                    "priority_rank", 2, "rationale", "r2"),
                Map.of("title", "Third", "description", "d3", "category", "todo",
                    "priority_rank", 3, "rationale", "r3")
            )
        ));

        var items = tool.getSelectedItems();
        assertEquals(3, items.size());
        assertEquals(1, items.get(0).priorityRank());
        assertEquals(2, items.get(1).priorityRank());
        assertEquals(3, items.get(2).priorityRank());
    }

    @Test
    void overwritesPreviousItems() throws Exception {
        var tool = new ReportPrioritizationTool();

        invoke(tool, Map.of(
            "selected_items", List.of(
                Map.of("title", "Old", "description", "d", "category", "todo",
                    "priority_rank", 1, "rationale", "r")
            )
        ));

        invoke(tool, Map.of(
            "selected_items", List.of(
                Map.of("title", "New", "description", "d2", "category", "security",
                    "priority_rank", 1, "rationale", "r2")
            )
        ));

        var items = tool.getSelectedItems();
        assertEquals(1, items.size());
        assertEquals("New", items.get(0).title());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(ReportPrioritizationTool tool, Map<String, Object> args) throws Exception {
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
