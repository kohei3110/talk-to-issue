package com.github.talktoissue.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueBuilder;
import org.kohsuke.github.GHRepository;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateIssueToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock GHRepository repository;

    @Test
    void dryRunCreatesFakeIssue() throws Exception {
        var tool = new CreateIssueTool(repository, true);
        var result = invoke(tool, Map.of("title", "Test", "body", "body text"));

        assertEquals("dry-run", result.get("status"));
        assertEquals(1, result.get("number"));
        assertEquals("Test", result.get("title"));
    }

    @Test
    void dryRunTracksCreatedIssues() throws Exception {
        var tool = new CreateIssueTool(repository, true);
        invoke(tool, Map.of("title", "First", "body", "body1"));
        invoke(tool, Map.of("title", "Second", "body", "body2"));

        var issues = tool.getCreatedIssues();
        assertEquals(2, issues.size());
        assertEquals("First", issues.get(0).title());
        assertEquals("Second", issues.get(1).title());
        assertEquals(1, issues.get(0).number());
        assertEquals(2, issues.get(1).number());
    }

    @Test
    void dryRunAutoIncrements() throws Exception {
        var tool = new CreateIssueTool(repository, true);
        invoke(tool, Map.of("title", "A", "body", "b"));
        invoke(tool, Map.of("title", "B", "body", "b"));
        invoke(tool, Map.of("title", "C", "body", "b"));

        assertEquals(3, tool.getCreatedIssues().size());
        assertEquals(3, tool.getCreatedIssues().get(2).number());
    }

    @Test
    void createsRealIssue() throws Exception {
        var mockBuilder = mock(GHIssueBuilder.class);
        var mockIssue = mock(GHIssue.class);

        when(repository.createIssue("Real Title")).thenReturn(mockBuilder);
        when(mockBuilder.body(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.create()).thenReturn(mockIssue);
        when(mockIssue.getNumber()).thenReturn(42);
        when(mockIssue.getTitle()).thenReturn("Real Title");
        when(mockIssue.getHtmlUrl()).thenReturn(new URL("https://github.com/test/repo/issues/42"));

        var tool = new CreateIssueTool(repository, false);
        var result = invoke(tool, Map.of("title", "Real Title", "body", "Real body"));

        assertEquals("created", result.get("status"));
        assertEquals(42, result.get("number"));
    }

    @Test
    void getCreatedIssuesReturnsCopy() throws Exception {
        var tool = new CreateIssueTool(repository, true);
        invoke(tool, Map.of("title", "X", "body", "b"));

        var list = tool.getCreatedIssues();
        assertThrows(UnsupportedOperationException.class, () -> list.add(null));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(CreateIssueTool tool, Map<String, Object> args) throws Exception {
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
