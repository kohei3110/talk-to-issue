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
    void toolNameIsCorrect() {
        var tool = new ReportDiscoveryTool();
        assertEquals("report_discovery", tool.build().name());
    }

    @Test
    void initialStateIsEmpty() {
        var tool = new ReportDiscoveryTool();
        assertTrue(tool.getDiscoveries().isEmpty());
    }

    @Test
    void capturesSingleDiscovery() throws Exception {
        var tool = new ReportDiscoveryTool();
        var result = invoke(tool, Map.of(
            "discoveries", List.of(
                Map.of(
                    "category", "todo",
                    "title", "Remove TODO in App.java",
                    "description", "There is a leftover TODO comment",
                    "severity", "low",
                    "affected_files", List.of("src/main/java/App.java"),
                    "estimated_effort", "small"
                )
            )
        ));

        assertEquals("received", result.get("status"));
        assertEquals(1, result.get("count"));

        var discoveries = tool.getDiscoveries();
        assertEquals(1, discoveries.size());
        assertEquals("todo", discoveries.get(0).category());
        assertEquals("Remove TODO in App.java", discoveries.get(0).title());
        assertEquals("low", discoveries.get(0).severity());
        assertEquals(List.of("src/main/java/App.java"), discoveries.get(0).affectedFiles());
        assertEquals("small", discoveries.get(0).estimatedEffort());
    }

    @Test
    void replacesOnSecondInvocation() throws Exception {
        var tool = new ReportDiscoveryTool();

        invoke(tool, Map.of(
            "discoveries", List.of(
                Map.of("category", "todo", "title", "First",
                    "description", "d1", "severity", "low",
                    "affected_files", List.of(), "estimated_effort", "small")
            )
        ));
        assertEquals(1, tool.getDiscoveries().size());

        invoke(tool, Map.of(
            "discoveries", List.of(
                Map.of("category", "security", "title", "Second",
                    "description", "d2", "severity", "high",
                    "affected_files", List.of("a.java", "b.java"), "estimated_effort", "large"),
                Map.of("category", "test_gap", "title", "Third",
                    "description", "d3", "severity", "medium",
                    "affected_files", List.of("c.java"), "estimated_effort", "medium")
            )
        ));

        var discoveries = tool.getDiscoveries();
        assertEquals(2, discoveries.size());
        assertEquals("Second", discoveries.get(0).title());
        assertEquals("Third", discoveries.get(1).title());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(ReportDiscoveryTool tool, Map<String, Object> args) throws Exception {
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
