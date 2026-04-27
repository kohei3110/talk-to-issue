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
class GetPullRequestReviewsToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock GHRepository repository;

    @SuppressWarnings("unchecked")
    @Test
    void fetchesReviewsAndComments() throws Exception {
        var pr = mock(GHPullRequest.class);
        var review = mock(GHPullRequestReview.class);
        var user = mock(GHUser.class);
        var reviewComment = mock(GHPullRequestReviewComment.class);
        var commentUser = mock(GHUser.class);

        when(user.getLogin()).thenReturn("reviewer1");
        when(review.getUser()).thenReturn(user);
        when(review.getState()).thenReturn(GHPullRequestReviewState.CHANGES_REQUESTED);
        when(review.getBody()).thenReturn("Please fix the null check");

        when(commentUser.getLogin()).thenReturn("reviewer1");
        when(reviewComment.getUser()).thenReturn(commentUser);
        when(reviewComment.getPath()).thenReturn("src/Main.java");
        when(reviewComment.getLine()).thenReturn(10);
        when(reviewComment.getBody()).thenReturn("Add null check here");
        when(reviewComment.getDiffHunk()).thenReturn("@@ -1,3 +1,3 @@");

        var reviewList = mock(PagedIterable.class);
        when(reviewList.toList()).thenReturn(List.of(review));
        when(pr.listReviews()).thenReturn(reviewList);

        var commentList = mock(PagedIterable.class);
        when(commentList.toList()).thenReturn(List.of(reviewComment));
        when(pr.listReviewComments()).thenReturn(commentList);

        when(repository.getPullRequest(5)).thenReturn(pr);

        var tool = new GetPullRequestReviewsTool(repository);
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(Map.of("pr_number", 5)));
        var result = (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);

        assertEquals(5, result.get("pr_number"));

        var reviews = (List<Map<String, Object>>) result.get("reviews");
        assertEquals(1, reviews.size());
        assertEquals("reviewer1", reviews.get(0).get("user"));
        assertEquals("CHANGES_REQUESTED", reviews.get(0).get("state"));

        var comments = (List<Map<String, Object>>) result.get("inline_comments");
        assertEquals(1, comments.size());
        assertEquals("src/Main.java", comments.get(0).get("path"));
        assertEquals("Add null check here", comments.get(0).get("body"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void handlesEmptyReviews() throws Exception {
        var pr = mock(GHPullRequest.class);

        var reviewList = mock(PagedIterable.class);
        when(reviewList.toList()).thenReturn(List.of());
        when(pr.listReviews()).thenReturn(reviewList);

        var commentList = mock(PagedIterable.class);
        when(commentList.toList()).thenReturn(List.of());
        when(pr.listReviewComments()).thenReturn(commentList);

        when(repository.getPullRequest(3)).thenReturn(pr);

        var tool = new GetPullRequestReviewsTool(repository);
        ToolDefinition def = tool.build();
        ToolInvocation invocation = new ToolInvocation();
        invocation.setArguments(MAPPER.valueToTree(Map.of("pr_number", 3)));
        var result = (Map<String, Object>) def.handler().invoke(invocation).get(5, TimeUnit.SECONDS);

        assertEquals(3, result.get("pr_number"));
        assertEquals(0, ((List<?>) result.get("reviews")).size());
        assertEquals(0, ((List<?>) result.get("inline_comments")).size());
    }
}
