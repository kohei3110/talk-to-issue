package com.github.talktoissue.commands;

import com.github.talktoissue.App;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class ServeCommandTest {

    @Test
    void parsesPortOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "serve", "--port", "9090");
        assertTrue(parseResult.hasSubcommand());
        assertEquals("serve", parseResult.subcommand().commandSpec().name());
    }

    @Test
    void parsesWebhookSecretOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "serve", "--webhook-secret", "mysecret");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesPollIntervalOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "serve", "--poll-interval", "5");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesTriggerLabelOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "serve", "--trigger-label", "my-label");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesConcurrencyOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-r", "owner/repo", "serve", "--concurrency", "4");
        assertTrue(parseResult.hasSubcommand());
    }
}
