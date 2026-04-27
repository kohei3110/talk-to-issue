package com.github.talktoissue.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateIssueToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock GHRepository repository;

    @Test
    void dryRunDoesNotUpdate() throws Exception {
        var result = invoke(true, Map.of("issue_number", 1, "body", "new body"));

        assertEquals("dry_run", result.get("status"));
        verifyNoInteractions(repository);
    }

    @Test
    void updatesIssueBody() throws Exception {
        var issue = mock(GHIssue.class);
        when(repository.getIssue(5)).thenReturn(issue);
        when(issue.getHtmlUrl()).thenReturn(new URL("https://github.com/test/repo/issues/5"));

        var result = invoke(false, Map.of("issue_number", 5, "body", "updated body"));

        assertEquals("updated", result.get("status"));
        assertEquals(5, result.get("issue_number"));
        verify(issue).setBody("updated body");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(boolean dryRun, Map<String, Object> args) throws Exception {
        var tool = new UpdateIssueTool(repository, dryRun);
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
