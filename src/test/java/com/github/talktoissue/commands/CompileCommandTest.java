package com.github.talktoissue.commands;

import com.github.talktoissue.App;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class CompileCommandTest {

    @Test
    void parsesFileOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "compile", "-f", "/tmp/notes.txt");
        assertTrue(parseResult.hasSubcommand());
        assertEquals("compile", parseResult.subcommand().commandSpec().name());
    }

    @Test
    void parsesContextConfigOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "compile", "--context-config", "config.yaml");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesWorkIQOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "compile", "-q", "summarize meetings", "--tenant-id", "tid");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesInlineContextOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "compile", "--context", "file:notes.txt,github:issues");
        assertTrue(parseResult.hasSubcommand());
    }
}
