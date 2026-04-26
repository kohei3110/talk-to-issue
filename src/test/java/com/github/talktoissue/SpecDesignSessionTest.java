package com.github.talktoissue;

import com.github.talktoissue.tools.CreateIssueTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the tools used by SpecDesignSession.
 * Full session integration requires CopilotClient auth, so we test tool wiring only.
 */
class SpecDesignSessionTest {

    @Test
    void createIssueToolNameIsCorrect() {
        var tool = new CreateIssueTool(null, false);
        var def = tool.build();
        assertEquals("create_issue", def.name());
        assertNotNull(def.handler());
    }

    @Test
    void createIssueToolStartsEmpty() {
        var tool = new CreateIssueTool(null, false);
        assertTrue(tool.getCreatedIssues().isEmpty());
    }
}
