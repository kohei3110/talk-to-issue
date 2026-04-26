package com.github.talktoissue.commands;

import com.github.talktoissue.App;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class AutonomousCommandTest {

    @Test
    void parsesDefaultOptions() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "-w", ".", "autonomous");
        assertTrue(parseResult.hasSubcommand());
        assertEquals("autonomous", parseResult.subcommand().commandSpec().name());
    }

    @Test
    void parsesMaxIssuesOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "-w", ".", "autonomous", "--max-issues", "5");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesMinScoreOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "-w", ".", "autonomous", "--min-score", "80");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesSkipReviewOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "-w", ".", "autonomous", "--skip-review");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesCategoriesOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "-w", ".", "autonomous", "--categories", "todo,security,test_gap");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesMaxRefineAndFixAttempts() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "-w", ".", "autonomous",
            "--max-refine-attempts", "5", "--max-fix-attempts", "2");
        assertTrue(parseResult.hasSubcommand());
    }
}
