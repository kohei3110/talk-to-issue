package com.github.talktoissue.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CreateBranchToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void createsBranchWithIssueNumber() throws Exception {
        // Initialize a git repo for this test
        new ProcessBuilder("git", "init").directory(tempDir.toFile()).start().waitFor();
        new ProcessBuilder("git", "config", "user.email", "test@test.com").directory(tempDir.toFile()).start().waitFor();
        new ProcessBuilder("git", "config", "user.name", "Test").directory(tempDir.toFile()).start().waitFor();
        java.nio.file.Files.writeString(tempDir.resolve("README.md"), "init");
        new ProcessBuilder("git", "add", ".").directory(tempDir.toFile()).start().waitFor();
        new ProcessBuilder("git", "commit", "-m", "init").directory(tempDir.toFile()).start().waitFor();

        var tool = new CreateBranchTool(tempDir.toFile());
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(Map.of("issue_number", 42)));

        @SuppressWarnings("unchecked")
        var result = (Map<String, Object>) def.handler().invoke(invocation).get(10, TimeUnit.SECONDS);

        assertEquals("created", result.get("status"));
        assertEquals("issue-42", result.get("branch"));
    }
}
