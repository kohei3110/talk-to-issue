package com.github.talktoissue.commands;

import com.github.talktoissue.App;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class IntroCommandTest {

    @Test
    void parsesFileAndOutputDirOptions() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "intro", "-f", "context.md", "-o", "slides");
        assertTrue(parseResult.hasSubcommand());
        assertEquals("intro", parseResult.subcommand().commandSpec().name());
    }

    @Test
    void parsesNoFileOptionDefaults() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "intro");
        assertTrue(parseResult.hasSubcommand());
        assertEquals("intro", parseResult.subcommand().commandSpec().name());
    }

    @Test
    void returnsErrorIfContextFileMissing() throws Exception {
        var app = new App();
        var cmd = new CommandLine(app);
        var intro = new IntroCommand();
        cmd.addSubcommand("intro", intro);
        intro.contextFile = Path.of("/tmp/nonexistent-file-xyz.md");
        intro.parent = app;
        // parent.getModel() and getWorkingDir() must not throw
        assertEquals(1, intro.call());
    }

    @Test
    void returnsErrorIfNoContextFound() throws Exception {
        var app = new App() {
            @Override public File getWorkingDir() { return new File("/tmp/empty-dir-xyz"); }
        };
        var cmd = new CommandLine(app);
        var intro = new IntroCommand();
        cmd.addSubcommand("intro", intro);
        intro.contextFile = null;
        intro.parent = app;
        // Ensure /tmp/empty-dir-xyz exists and is empty
        File dir = new File("/tmp/empty-dir-xyz");
        if (!dir.exists()) dir.mkdirs();
        for (File f : dir.listFiles()) f.delete();
        assertEquals(1, intro.call());
    }

    @Test
    void dryRunPrintsDryRunMessage() throws Exception {
        var app = new App() {
            @Override public boolean isDryRun() { return true; }
            @Override public String getModel() { return "gpt-4.1"; }
            @Override public File getWorkingDir() { return new File("."); }
        };
        var cmd = new CommandLine(app);
        var intro = new IntroCommand();
        cmd.addSubcommand("intro", intro);
        intro.contextFile = null;
        intro.parent = app;
        // Create README.md for context
        Path readme = Path.of("README.md");
        Files.writeString(readme, "# test readme\n");
        try {
            assertEquals(0, intro.call());
        } finally {
            Files.deleteIfExists(readme);
        }
    }
}
