package com.github.talktoissue;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    @Test
    void parsesRepoOption() {
        var app = new App();
        new CommandLine(app).parseArgs("-r", "owner/repo");
        assertEquals("owner/repo", app.getRepoFullName());
    }

    @Test
    void parsesModelDefault() {
        var app = new App();
        new CommandLine(app).parseArgs("-r", "owner/repo");
        assertEquals("gpt-4.1", app.getModel());
    }

    @Test
    void parsesModelOverride() {
        var app = new App();
        new CommandLine(app).parseArgs("-r", "owner/repo", "-m", "claude-sonnet-4");
        assertEquals("claude-sonnet-4", app.getModel());
    }

    @Test
    void parsesDryRunDefault() {
        var app = new App();
        new CommandLine(app).parseArgs("-r", "owner/repo");
        assertFalse(app.isDryRun());
    }

    @Test
    void parsesDryRunExplicit() {
        var app = new App();
        new CommandLine(app).parseArgs("-r", "owner/repo", "--dry-run");
        assertTrue(app.isDryRun());
    }

    @Test
    void parsesWorkingDir() {
        var app = new App();
        new CommandLine(app).parseArgs("-r", "owner/repo", "-w", "/tmp");
        assertNotNull(app.getWorkingDir());
        assertEquals("tmp", app.getWorkingDir().getName());
    }

    @Test
    void workingDirDefaultsToNull() {
        var app = new App();
        new CommandLine(app).parseArgs("-r", "owner/repo");
        assertNull(app.getWorkingDir());
    }

    @Test
    void subcommandsRegistered() {
        var cmd = new CommandLine(new App());
        var subcommands = cmd.getSubcommands();
        assertTrue(subcommands.containsKey("run"));
        assertTrue(subcommands.containsKey("compile"));
        assertTrue(subcommands.containsKey("score"));
        assertTrue(subcommands.containsKey("drift"));
        assertTrue(subcommands.containsKey("pipeline"));
        assertTrue(subcommands.containsKey("serve"));
    }
}
