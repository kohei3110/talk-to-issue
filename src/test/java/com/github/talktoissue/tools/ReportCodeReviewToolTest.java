package com.github.talktoissue.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReportCodeReviewToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void initialStateIsNull() {
        var tool = new ReportCodeReviewTool();
        assertNull(tool.getCodeReview());
    }

    @Test
    void capturesCodeReview() throws Exception {
        var tool = new ReportCodeReviewTool();
        var result = invoke(tool, Map.of(
            "verdict", "request_changes",
            "summary", "Security issues found",
            "findings", List.of(
                Map.of("severity", "critical", "category", "security",
                    "file", "Auth.java", "line", 42,
                    "description", "SQL injection", "suggestion", "Use prepared statements")
            ),
            "positives", List.of("Good test coverage")
        ));

        assertEquals("received", result.get("status"));
        assertEquals("request_changes", result.get("verdict"));
        assertEquals(1, result.get("findingCount"));

        var review = tool.getCodeReview();
        assertNotNull(review);
        assertEquals("request_changes", review.verdict());
        assertEquals(1, review.findings().size());
        assertEquals("critical", review.findings().get(0).severity());
        assertEquals("security", review.findings().get(0).category());
        assertEquals(42, review.findings().get(0).line());
        assertEquals(1, review.positives().size());
    }

    @Test
    void approveVerdictWithNoFindings() throws Exception {
        var tool = new ReportCodeReviewTool();
        var result = invoke(tool, Map.of(
            "verdict", "approve",
            "summary", "LGTM",
            "findings", List.of(),
            "positives", List.of("Clean code")
        ));

        assertEquals("approve", result.get("verdict"));
        assertEquals(0, result.get("findingCount"));
        assertEquals("approve", tool.getCodeReview().verdict());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(ReportCodeReviewTool tool, Map<String, Object> args) throws Exception {
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
