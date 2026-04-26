package com.github.talktoissue;

import com.github.talktoissue.tools.ReportPrioritizationTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ReportPrioritizationTool used by PrioritizationSession.
 * Full session integration requires CopilotClient auth, so we test tool wiring only.
 */
class PrioritizationSessionTest {

    @Test
    void prioritizationToolNameIsCorrect() {
        var tool = new ReportPrioritizationTool();
        var def = tool.build();
        assertEquals("report_prioritization", def.name());
        assertNotNull(def.handler());
    }

    @Test
    void prioritizationToolStartsEmpty() {
        var tool = new ReportPrioritizationTool();
        assertTrue(tool.getSelectedItems().isEmpty());
    }
}
