package com.github.talktoissue;

import com.github.talktoissue.tools.ReportDiscoveryTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ReportDiscoveryTool used by CodebaseAnalysisSession.
 * Full session integration requires CopilotClient auth, so we test tool wiring only.
 */
class CodebaseAnalysisSessionTest {

    @Test
    void discoveryToolNameIsCorrect() {
        var tool = new ReportDiscoveryTool();
        var def = tool.build();
        assertEquals("report_discovery", def.name());
        assertNotNull(def.handler());
    }

    @Test
    void discoveryToolStartsEmpty() {
        var tool = new ReportDiscoveryTool();
        assertTrue(tool.getDiscoveries().isEmpty());
    }
}
