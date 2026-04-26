package com.github.talktoissue.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReportQualityScoreToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void initialStateIsNull() {
        var tool = new ReportQualityScoreTool();
        assertNull(tool.getQualityScore());
    }

    @Test
    void capturesQualityScore() throws Exception {
        var tool = new ReportQualityScoreTool();
        var result = invoke(tool, Map.of(
            "overallScore", 85,
            "dimensions", List.of(
                Map.of("name", "Clarity", "score", 90, "feedback", "Clear"),
                Map.of("name", "Specificity", "score", 80, "feedback", "Specific")
            ),
            "suggestions", List.of("Add more details")
        ));

        assertEquals("received", result.get("status"));
        assertEquals(85, result.get("overallScore"));
        assertEquals(2, result.get("dimensionCount"));

        var score = tool.getQualityScore();
        assertNotNull(score);
        assertEquals(85, score.overallScore());
        assertEquals(2, score.dimensions().size());
        assertEquals("Clarity", score.dimensions().get(0).name());
        assertEquals(90, score.dimensions().get(0).score());
        assertEquals("Clear", score.dimensions().get(0).feedback());
        assertEquals(1, score.suggestions().size());
        assertEquals("Add more details", score.suggestions().get(0));
    }

    @Test
    void overwritesPreviousScore() throws Exception {
        var tool = new ReportQualityScoreTool();

        invoke(tool, Map.of(
            "overallScore", 50,
            "dimensions", List.of(Map.of("name", "A", "score", 50, "feedback", "avg")),
            "suggestions", List.of()
        ));

        invoke(tool, Map.of(
            "overallScore", 95,
            "dimensions", List.of(Map.of("name", "B", "score", 95, "feedback", "great")),
            "suggestions", List.of("none")
        ));

        var score = tool.getQualityScore();
        assertEquals(95, score.overallScore());
        assertEquals("B", score.dimensions().get(0).name());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(ReportQualityScoreTool tool, Map<String, Object> args) throws Exception {
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
