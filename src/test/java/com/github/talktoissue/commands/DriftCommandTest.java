package com.github.talktoissue.commands;

import com.github.talktoissue.App;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class DriftCommandTest {

    @Test
    void parsesPROption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "drift", "--pr", "10");
        assertTrue(parseResult.hasSubcommand());
        assertEquals("drift", parseResult.subcommand().commandSpec().name());
    }

    @Test
    void parsesLinkedIssueOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "drift", "--pr", "10", "--issue", "5");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesTranscriptFileOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "drift", "--pr", "10", "-f", "notes.txt");
        assertTrue(parseResult.hasSubcommand());
    }
}
