package com.github.talktoissue.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventRouterTest {

    @Mock WorkQueue workQueue;

    private EventRouter createRouter() {
        return new EventRouter(workQueue, "owner/repo", new File("."),
                "gpt-4o", true, "agent-ready", null);
    }

    @Test
    void ignoresUnknownEventType() {
        var router = createRouter();
        router.route("deployment", "{}");
        verifyNoInteractions(workQueue);
    }

    @Test
    void ignoresIssueWithWrongLabel() {
        var router = createRouter();
        String payload = """
            {"action": "labeled", "label": {"name": "bug"},
             "issue": {"number": 1, "title": "Test"}}
            """;
        router.route("issues", payload);
        verifyNoInteractions(workQueue);
    }

    @Test
    void triggersOnCorrectLabel() {
        var router = createRouter();
        String payload = """
            {"action": "labeled", "label": {"name": "agent-ready"},
             "issue": {"number": 42, "title": "Feature"}}
            """;
        router.route("issues", payload);
        verify(workQueue).submit(eq("owner/repo"), eq("implement-issue-42"), any());
    }

    @Test
    void ignoresNonLabeledAction() {
        var router = createRouter();
        String payload = """
            {"action": "opened", "issue": {"number": 1}}
            """;
        router.route("issues", payload);
        verifyNoInteractions(workQueue);
    }

    @Test
    void handlesPipelineComment() {
        var router = createRouter();
        String payload = """
            {"action": "created",
             "comment": {"body": "/pipeline"},
             "issue": {"number": 10}}
            """;
        router.route("issue_comment", payload);
        verify(workQueue).submit(eq("owner/repo"), eq("pipeline-from-comment-10"), any());
    }

    @Test
    void ignoresNonPipelineComment() {
        var router = createRouter();
        String payload = """
            {"action": "created",
             "comment": {"body": "looks good"},
             "issue": {"number": 10}}
            """;
        router.route("issue_comment", payload);
        verifyNoInteractions(workQueue);
    }

    @Test
    void ignoresPullRequestEvent() {
        var router = createRouter();
        String payload = """
            {"action": "opened",
             "pull_request": {"number": 5, "head": {"ref": "issue-42"}}}
            """;
        router.route("pull_request", payload);
        verifyNoInteractions(workQueue);
    }

    @Test
    void ignoresPRWithNonIssueBranch() {
        var router = createRouter();
        String payload = """
            {"action": "opened",
             "pull_request": {"number": 5, "head": {"ref": "feature-branch"}}}
            """;
        router.route("pull_request", payload);
        verifyNoInteractions(workQueue);
    }

    @Test
    void ignoresPRSynchronize() {
        var router = createRouter();
        String payload = """
            {"action": "synchronize",
             "pull_request": {"number": 7, "head": {"ref": "issue-99"}}}
            """;
        router.route("pull_request", payload);
        verifyNoInteractions(workQueue);
    }

    @Test
    void ignoresPRClosedAction() {
        var router = createRouter();
        String payload = """
            {"action": "closed",
             "pull_request": {"number": 1, "head": {"ref": "issue-1"}}}
            """;
        router.route("pull_request", payload);
        verifyNoInteractions(workQueue);
    }

    @Test
    void customTriggerLabel() {
        var router = new EventRouter(workQueue, "owner/repo", new File("."),
                "gpt-4o", true, "custom-trigger", null);
        String payload = """
            {"action": "labeled", "label": {"name": "custom-trigger"},
             "issue": {"number": 1, "title": "Test"}}
            """;
        router.route("issues", payload);
        verify(workQueue).submit(eq("owner/repo"), eq("implement-issue-1"), any());
    }

    @Test
    void malformedJsonDoesNotThrow() {
        var router = createRouter();
        assertDoesNotThrow(() -> router.route("issues", "not json"));
    }
}
