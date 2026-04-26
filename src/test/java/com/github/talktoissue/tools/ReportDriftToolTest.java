package com.github.talktoissue.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReportDriftToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void initialStateIsNull() {
        var tool = new ReportDriftTool();
        assertNull(tool.getDriftReport());
    }

    @Test
    void capturesDriftReport() throws Exception {
        var tool = new ReportDriftTool();
        var result = invoke(tool, Map.of(
            "verdict", "warn",
            "overallSummary", "Minor drift detected",
            "drifts", List.of(
                Map.of("type", "scope_creep", "severity", "low", "file", "Main.java",
                    "description", "Added unrelated method")
            ),
            "coverageOfRequirements", List.of(
                Map.of("requirement", "Add login", "status", "met", "evidence", "LoginController added")
            ),
            "suggestions", List.of("Remove unrelated method")
        ));

        assertEquals("received", result.get("status"));
        assertEquals("warn", result.get("verdict"));

        var report = tool.getDriftReport();
        assertNotNull(report);
        assertEquals("warn", report.verdict());
        assertEquals(1, report.drifts().size());
        assertEquals("scope_creep", report.drifts().get(0).type());
        assertEquals(1, report.coverageOfRequirements().size());
        assertEquals("met", report.coverageOfRequirements().get(0).status());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(ReportDriftTool tool, Map<String, Object> args) throws Exception {
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
