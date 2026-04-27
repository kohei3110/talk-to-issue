package com.github.talktoissue.commands;

import com.github.talktoissue.App;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class PipelineCommandTest {

    @Test
    void parsesFileOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "pipeline", "-f", "meeting.txt");
        assertTrue(parseResult.hasSubcommand());
        assertEquals("pipeline", parseResult.subcommand().commandSpec().name());
    }

    @Test
    void parsesMinScoreOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "pipeline", "-f", "m.txt", "--min-score", "80");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesContextConfigOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "pipeline", "--context-config", "config.yaml");
        assertTrue(parseResult.hasSubcommand());
    }
}
