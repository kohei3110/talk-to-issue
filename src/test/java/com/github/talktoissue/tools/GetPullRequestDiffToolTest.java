package com.github.talktoissue.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.json.ToolInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetPullRequestDiffToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock GHRepository repository;

    @SuppressWarnings("unchecked")
    @Test
    void fetchesPRDiff() throws Exception {
        var pr = mock(GHPullRequest.class);
        var head = mock(GHCommitPointer.class);
        var base = mock(GHCommitPointer.class);
        var file = mock(GHPullRequestFileDetail.class);

        when(pr.getNumber()).thenReturn(7);
        when(pr.getTitle()).thenReturn("Feature PR");
        when(pr.getBody()).thenReturn("PR body");
        when(pr.getState()).thenReturn(GHIssueState.OPEN);
        when(pr.getHead()).thenReturn(head);
        when(pr.getBase()).thenReturn(base);
        when(head.getRef()).thenReturn("issue-7");
        when(base.getRef()).thenReturn("main");
        when(pr.getAdditions()).thenReturn(10);
        when(pr.getDeletions()).thenReturn(2);

        when(file.getFilename()).thenReturn("src/Main.java");
        when(file.getStatus()).thenReturn("modified");
        when(file.getAdditions()).thenReturn(8);
        when(file.getDeletions()).thenReturn(2);
        when(file.getPatch()).thenReturn("@@ -1,3 +1,3 @@");

        var fileList = mock(PagedIterable.class);
        when(fileList.toList()).thenReturn(List.of(file));
        when(pr.listFiles()).thenReturn(fileList);
        when(repository.getPullRequest(7)).thenReturn(pr);

        var tool = new GetPullRequestDiffTool(repository);
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(Map.of("pr_number", 7)));
        var result = (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);

        assertEquals(7, result.get("number"));
        assertEquals("Feature PR", result.get("title"));
        assertEquals(10, result.get("additions"));

        var changedFiles = (List<Map<String, Object>>) result.get("changedFiles");
        assertEquals(1, changedFiles.size());
        assertEquals("src/Main.java", changedFiles.get(0).get("filename"));
    }
}
