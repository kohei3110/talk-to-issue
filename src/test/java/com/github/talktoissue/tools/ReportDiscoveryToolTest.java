package com.github.talktoissue.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReportDiscoveryToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void initialStateIsEmpty() {
        var tool = new ReportDiscoveryTool();
        assertTrue(tool.getDiscoveries().isEmpty());
    }

    @Test
    void capturesSingleDiscovery() throws Exception {
        var tool = new ReportDiscoveryTool();
        var result = invoke(tool, Map.of(
            "discoveries", List.of(Map.of(
                "category", "todo",
                "title", "Remove TODO in App.java",
                "description", "There is a leftover TODO on line 42",
                "severity", "low",
                "affected_files", List.of("src/main/java/App.java"),
                "estimated_effort", "small"
            ))
        ));

        assertEquals("received", result.get("status"));
        assertEquals(1, result.get("count"));

        var discoveries = tool.getDiscoveries();
        assertEquals(1, discoveries.size());
        assertEquals("todo", discoveries.get(0).category());
        assertEquals("Remove TODO in App.java", discoveries.get(0).title());
        assertEquals("low", discoveries.get(0).severity());
        assertEquals("small", discoveries.get(0).estimatedEffort());
        assertEquals(1, discoveries.get(0).affectedFiles().size());
    }

    @Test
    void replacesOnSecondInvocation() throws Exception {
        var tool = new ReportDiscoveryTool();

        invoke(tool, Map.of(
            "discoveries", List.of(Map.of(
                "category", "security",
                "title", "SQL injection risk",
                "description", "Unparameterized query",
                "severity", "high",
                "affected_files", List.of("Dao.java"),
                "estimated_effort", "medium"
            ))
        ));
        assertEquals(1, tool.getDiscoveries().size());

        invoke(tool, Map.of(
            "discoveries", List.of(
                Map.of("category", "test_gap", "title", "Missing tests",
                    "description", "No tests", "severity", "medium",
                    "affected_files", List.of("A.java"), "estimated_effort", "large"),
                Map.of("category", "tech_debt", "title", "Cleanup",
                    "description", "Old code", "severity", "low",
                    "affected_files", List.of("B.java"), "estimated_effort", "small")
            )
        ));

        // Second invocation replaces the first
        assertEquals(2, tool.getDiscoveries().size());
        assertEquals("test_gap", tool.getDiscoveries().get(0).category());
        assertEquals("tech_debt", tool.getDiscoveries().get(1).category());
    }

    @Test
    void toolDefinitionHasCorrectName() {
        var tool = new ReportDiscoveryTool();
        ToolDefinition def = tool.build();
        assertEquals("report_discovery", def.name());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(ReportDiscoveryTool tool, Map<String, Object> args) throws Exception {
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
