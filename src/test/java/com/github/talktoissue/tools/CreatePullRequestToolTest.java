package com.github.talktoissue.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatePullRequestToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock GHRepository repository;

    @Test
    void dryRunReturnsPRInfo() throws Exception {
        var result = invoke(true, Map.of("title", "Add feature", "body", "Description", "issue_number", 5));

        assertEquals("dry-run", result.get("status"));
        assertEquals("Add feature", result.get("title"));
        assertEquals("issue-5", result.get("head"));
        assertEquals("main", result.get("base"));
    }

    @Test
    void createsRealPR() throws Exception {
        var pr = mock(GHPullRequest.class);
        when(pr.getNumber()).thenReturn(10);
        when(pr.getTitle()).thenReturn("Add feature");
        when(pr.getHtmlUrl()).thenReturn(new URL("https://github.com/test/repo/pull/10"));
        when(repository.createPullRequest(anyString(), anyString(), anyString(), anyString())).thenReturn(pr);

        var result = invoke(false, Map.of("title", "Add feature", "body", "Description", "issue_number", 5));

        assertEquals("created", result.get("status"));
        assertEquals(10, result.get("number"));
        verify(repository).createPullRequest(eq("Add feature"), eq("issue-5"), eq("main"), contains("Closes #5"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(boolean dryRun, Map<String, Object> args) throws Exception {
        var tool = new CreatePullRequestTool(repository, dryRun);
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(args));
        return (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);
    }
}
