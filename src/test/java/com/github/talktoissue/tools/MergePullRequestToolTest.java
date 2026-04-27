package com.github.talktoissue.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MergePullRequestToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock GHRepository repository;

    @SuppressWarnings("unchecked")
    @Test
    void dryRunDoesNotMerge() throws Exception {
        var tool = new MergePullRequestTool(repository, true);
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(Map.of("pr_number", 10, "commit_message", "Squash merge")));
        var result = (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);

        assertEquals("dry-run", result.get("status"));
        assertEquals(10, result.get("pr_number"));
        verifyNoInteractions(repository);
    }

    @SuppressWarnings("unchecked")
    @Test
    void mergesWhenNotDryRun() throws Exception {
        var pr = mock(GHPullRequest.class);
        var head = mock(GHCommitPointer.class);
        when(head.getSha()).thenReturn("abc123");
        when(pr.getMergeable()).thenReturn(true);
        when(pr.getHead()).thenReturn(head);
        when(repository.getPullRequest(10)).thenReturn(pr);

        var tool = new MergePullRequestTool(repository, false);
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(Map.of("pr_number", 10, "commit_message", "Squash merge")));
        var result = (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);

        assertEquals("merged", result.get("status"));
        verify(pr).merge("Squash merge", "abc123", GHPullRequest.MergeMethod.SQUASH);
    }

    @SuppressWarnings("unchecked")
    @Test
    void rejectsNonMergeablePR() throws Exception {
        var pr = mock(GHPullRequest.class);
        when(pr.getMergeable()).thenReturn(false);
        when(repository.getPullRequest(10)).thenReturn(pr);

        var tool = new MergePullRequestTool(repository, false);
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(Map.of("pr_number", 10, "commit_message", "Merge")));
        var result = (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);

        assertEquals("error", result.get("status"));
        assertTrue(((String) result.get("message")).contains("not mergeable"));
    }
}
