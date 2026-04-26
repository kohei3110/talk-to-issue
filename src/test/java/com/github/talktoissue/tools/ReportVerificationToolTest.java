package com.github.talktoissue.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReportVerificationToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void initialStateIsNull() {
        var tool = new ReportVerificationTool();
        assertNull(tool.getVerificationResult());
    }

    @Test
    void capturesSuccessfulVerification() throws Exception {
        var tool = new ReportVerificationTool();
        var result = invoke(tool, Map.of(
            "buildSuccess", true,
            "testsSuccess", true,
            "buildOutput", "BUILD SUCCESS",
            "testOutput", "Tests run: 10, Failures: 0",
            "failures", List.of(),
            "suggestions", List.of()
        ));

        assertEquals("received", result.get("status"));
        assertTrue((Boolean) result.get("buildSuccess"));
        assertTrue((Boolean) result.get("testsSuccess"));
        assertEquals(0, result.get("failureCount"));

        var verification = tool.getVerificationResult();
        assertTrue(verification.buildSuccess());
        assertTrue(verification.testsSuccess());
    }

    @Test
    void capturesFailedVerification() throws Exception {
        var tool = new ReportVerificationTool();
        invoke(tool, Map.of(
            "buildSuccess", true,
            "testsSuccess", false,
            "buildOutput", "",
            "testOutput", "FAILURES",
            "failures", List.of(
                Map.of("testName", "testLogin", "message", "AssertionError", "file", "LoginTest.java")
            ),
            "suggestions", List.of("Fix assertion")
        ));

        var verification = tool.getVerificationResult();
        assertTrue(verification.buildSuccess());
        assertFalse(verification.testsSuccess());
        assertEquals(1, verification.failures().size());
        assertEquals("testLogin", verification.failures().get(0).testName());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(ReportVerificationTool tool, Map<String, Object> args) throws Exception {
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
