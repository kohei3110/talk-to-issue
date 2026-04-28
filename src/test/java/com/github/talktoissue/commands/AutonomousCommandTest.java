package com.github.talktoissue.commands;

import com.github.talktoissue.App;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class AutonomousCommandTest {
    @Test
    void parsesCategoriesOption_todo() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("autonomous", "--categories", "todo");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesCategoriesOption_test_gap() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("autonomous", "--categories", "test_gap");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesCategoriesOption_security() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("autonomous", "--categories", "security");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesCategoriesOption_tech_debt() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("autonomous", "--categories", "tech_debt");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesCategoriesOption_error_handling() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("autonomous", "--categories", "error_handling");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesCategoriesOption_documentation() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("autonomous", "--categories", "documentation");
        assertTrue(parseResult.hasSubcommand());
    }
}
