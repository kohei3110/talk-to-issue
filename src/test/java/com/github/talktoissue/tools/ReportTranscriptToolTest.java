package com.github.talktoissue.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReportTranscriptToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void initialStateIsNull() {
        var tool = new ReportTranscriptTool();
        assertNull(tool.getCompiledTranscript());
    }

    @Test
    void capturesTranscript() throws Exception {
        var tool = new ReportTranscriptTool();
        var result = invoke(tool, Map.of(
            "meetingTitle", "Sprint Planning",
            "meetingDate", "2026-04-25",
            "transcript", "Discussion about new features..."
        ));

        assertEquals("received", result.get("status"));
        assertEquals("Sprint Planning", result.get("meetingTitle"));
        assertEquals("2026-04-25", result.get("meetingDate"));

        var transcript = tool.getCompiledTranscript();
        assertNotNull(transcript);
        assertEquals("Sprint Planning", transcript.meetingTitle());
        assertEquals("2026-04-25", transcript.meetingDate());
        assertTrue(transcript.transcript().contains("Discussion"));
    }

    @Test
    void optionalMeetingDateDefaultsToUnknown() throws Exception {
        var tool = new ReportTranscriptTool();
        invoke(tool, Map.of(
            "meetingTitle", "Quick Sync",
            "transcript", "Brief meeting"
        ));

        assertEquals("unknown", tool.getCompiledTranscript().meetingDate());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(ReportTranscriptTool tool, Map<String, Object> args) throws Exception {
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
