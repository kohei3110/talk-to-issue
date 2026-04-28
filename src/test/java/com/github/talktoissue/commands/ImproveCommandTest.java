package com.github.talktoissue.commands;

import com.github.talktoissue.App;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class ImproveCommandTest {

    @Test
    void parsesTargetDirOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("improve", "--target-dir", "/tmp/project");
        assertTrue(parseResult.hasSubcommand());
        assertEquals("improve", parseResult.subcommand().commandSpec().name());
    }

    @Test
    void parsesMaxIssuesOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("improve", "--target-dir", "/tmp/project", "--max-issues", "5");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesMaxFixAttemptsOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("improve", "--target-dir", "/tmp/project", "--max-fix-attempts", "5");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesCategoriesOption_todo() {
        var cmd = new CommandLine(new App());
        // 正常系: todoのみ
        var parseResult = cmd.parseArgs("improve", "--target-dir", "/tmp/project", "--categories", "todo");
        assertTrue(parseResult.hasSubcommand());
        assertEquals("improve", parseResult.subcommand().commandSpec().name());
        // 異常系: 不正カテゴリ
        var ex = assertThrows(CommandLine.ParameterException.class, () ->
            cmd.parseArgs("improve", "--target-dir", "/tmp/project", "--categories", "unknown_category"));
        assertTrue(ex.getMessage().contains("unknown_category"));
        // 複数カテゴリ
        var parseResult2 = cmd.parseArgs("improve", "--target-dir", "/tmp/project", "--categories", "todo,security");
        assertTrue(parseResult2.hasSubcommand());
    }

    @Test
    void parsesCategoriesOption_test_gap() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("improve", "--target-dir", "/tmp/project", "--categories", "test_gap");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesCategoriesOption_security() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("improve", "--target-dir", "/tmp/project", "--categories", "security");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesCategoriesOption_tech_debt() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("improve", "--target-dir", "/tmp/project", "--categories", "tech_debt");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesCategoriesOption_error_handling() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("improve", "--target-dir", "/tmp/project", "--categories", "error_handling");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesCategoriesOption_documentation() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("improve", "--target-dir", "/tmp/project", "--categories", "documentation");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void parsesIntervalOption() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("improve", "--target-dir", "/tmp/project", "--interval", "5");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void requiresTargetDir() {
        var cmd = new CommandLine(new App());
        assertThrows(CommandLine.MissingParameterException.class,
                () -> cmd.parseArgs("improve"));
    }

    @Test
    void doesNotRequireRepoOption() {
        var cmd = new CommandLine(new App());
        // improve should work without --repo
        var parseResult = cmd.parseArgs("improve", "--target-dir", "/tmp/project");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void worksWithDryRunFromParent() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("--dry-run", "improve", "--target-dir", "/tmp/project");
        assertTrue(parseResult.hasSubcommand());
    }

    @Test
    void worksWithModelFromParent() {
        var cmd = new CommandLine(new App());
        var parseResult = cmd.parseArgs("-m", "claude-sonnet-4", "improve", "--target-dir", "/tmp/project");
        assertTrue(parseResult.hasSubcommand());
    }
}
