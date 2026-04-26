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
    void initialStateIsEmpty() {
        var tool = new ReportPrioritizationTool();
        assertTrue(tool.getSelectedItems().isEmpty());
    }

    @Test
    void capturesSinglePrioritizedItem() throws Exception {
        var tool = new ReportPrioritizationTool();
        var result = invoke(tool, Map.of(
            "selected_items", List.of(Map.of(
                "title", "Fix SQL injection",
                "description", "Parameterize queries",
                "category", "security",
                "priority_rank", 1,
                "rationale", "High security impact"
            ))
        ));

        assertEquals("received", result.get("status"));
        assertEquals(1, result.get("count"));

        var items = tool.getSelectedItems();
        assertEquals(1, items.size());
        assertEquals("Fix SQL injection", items.get(0).title());
        assertEquals("security", items.get(0).category());
        assertEquals(1, items.get(0).priorityRank());
        assertEquals("High security impact", items.get(0).rationale());
    }

    @Test
    void capturesMultipleItems() throws Exception {
        var tool = new ReportPrioritizationTool();
        invoke(tool, Map.of(
            "selected_items", List.of(
                Map.of("title", "A", "description", "desc A", "category", "security",
                        "priority_rank", 1, "rationale", "critical"),
                Map.of("title", "B", "description", "desc B", "category", "test_gap",
                        "priority_rank", 2, "rationale", "important"),
                Map.of("title", "C", "description", "desc C", "category", "tech_debt",
                        "priority_rank", 3, "rationale", "nice to have")
            )
        ));

        assertEquals(3, tool.getSelectedItems().size());
        assertEquals(1, tool.getSelectedItems().get(0).priorityRank());
        assertEquals(3, tool.getSelectedItems().get(2).priorityRank());
    }

    @Test
    void overwritesPreviousItems() throws Exception {
        var tool = new ReportPrioritizationTool();

        invoke(tool, Map.of(
            "selected_items", List.of(
                Map.of("title", "Old", "description", "old", "category", "todo",
                        "priority_rank", 1, "rationale", "r")
            )
        ));

        invoke(tool, Map.of(
            "selected_items", List.of(
                Map.of("title", "New", "description", "new", "category", "security",
                        "priority_rank", 1, "rationale", "r2")
            )
        ));

        assertEquals(1, tool.getSelectedItems().size());
        assertEquals("New", tool.getSelectedItems().get(0).title());
    }

    @Test
    void toolDefinitionHasCorrectName() {
        var tool = new ReportPrioritizationTool();
        ToolDefinition def = tool.build();
        assertEquals("report_prioritization", def.name());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(ReportPrioritizationTool tool, Map<String, Object> args) throws Exception {
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
