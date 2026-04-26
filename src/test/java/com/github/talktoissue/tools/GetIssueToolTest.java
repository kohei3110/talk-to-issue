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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetIssueToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock GHRepository repository;

    @Test
    void fetchesIssueDetails() throws Exception {
        var issue = mock(GHIssue.class);
        when(issue.getNumber()).thenReturn(42);
        when(issue.getTitle()).thenReturn("Test Issue");
        when(issue.getBody()).thenReturn("Issue body");
        when(issue.getState()).thenReturn(GHIssueState.OPEN);
        when(issue.getLabels()).thenReturn(List.of());
        when(issue.getAssignees()).thenReturn(List.of());
        when(issue.getHtmlUrl()).thenReturn(new URL("https://github.com/test/repo/issues/42"));
        when(repository.getIssue(42)).thenReturn(issue);

        var result = invoke(Map.of("issue_number", 42));

        assertEquals(42, result.get("number"));
        assertEquals("Test Issue", result.get("title"));
        assertEquals("Issue body", result.get("body"));
    }

    @Test
    void issueWithNullBody() throws Exception {
        var issue = mock(GHIssue.class);
        when(issue.getNumber()).thenReturn(1);
        when(issue.getTitle()).thenReturn("No body");
        when(issue.getBody()).thenReturn(null);
        when(issue.getState()).thenReturn(GHIssueState.OPEN);
        when(issue.getLabels()).thenReturn(List.of());
        when(issue.getAssignees()).thenReturn(List.of());
        when(issue.getHtmlUrl()).thenReturn(new URL("https://github.com/test/repo/issues/1"));
        when(repository.getIssue(1)).thenReturn(issue);

        var result = invoke(Map.of("issue_number", 1));
        assertEquals("", result.get("body"));
    }

    @Test
    void issueNotFoundReturnsError() throws Exception {
        when(repository.getIssue(999)).thenThrow(new java.io.FileNotFoundException("Not found"));

        var result = invoke(Map.of("issue_number", 999));
        assertEquals("error", result.get("status"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(Map<String, Object> args) throws Exception {
        var tool = new GetIssueTool(repository);
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
