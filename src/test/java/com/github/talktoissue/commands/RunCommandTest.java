package com.github.talktoissue.commands;

import com.github.talktoissue.App;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class RunCommandTest {

    @Test
    void parsesFileOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "run", "-f", "/tmp/transcript.txt");
        assertTrue(parseResult.hasSubcommand());
        assertEquals("run", parseResult.subcommand().commandSpec().name());
    }

    @Test
    void parsesWorkIQOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "run", "-q", "last meeting", "--tenant-id", "abc");
        assertTrue(parseResult.hasSubcommand());
    }
}
