package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GenerateSlideToolTest {
    @Test
    void testGetGeneratedFilePathInitiallyNull() {
        GenerateSlideTool tool = new GenerateSlideTool(new File("/tmp"), true);
        assertNull(tool.getGeneratedFilePath());
    }

    @Test
    void testDryRunSlideGeneration() throws Exception {
        GenerateSlideTool tool = new GenerateSlideTool(new File("/tmp"), true);
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(com.fasterxml.jackson.databind.json.JsonMapper.builder().build().valueToTree(Map.of(
            "slides", List.of(Map.of("title", "Test Slide")),
            "output_filename", "test.pptx"
        )));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
        assertEquals("dry-run", result.get("status"));
        assertEquals(1, result.get("slides_count"));
        assertEquals("test.pptx", result.get("filename"));
    }
}
