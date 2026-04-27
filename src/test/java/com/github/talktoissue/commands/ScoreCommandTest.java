package com.github.talktoissue.commands;

import com.github.talktoissue.App;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class ScoreCommandTest {

    @Test
    void parsesIssueOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "score", "--issue", "42");
        assertTrue(parseResult.hasSubcommand());
        assertEquals("score", parseResult.subcommand().commandSpec().name());
    }

    @Test
    void parsesMinScoreOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "score", "--issue", "1", "--min-score", "80");
        assertTrue(parseResult.hasSubcommand());
    }
}
