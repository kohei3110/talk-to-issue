package com.github.talktoissue;

import com.github.talktoissue.commands.CompileCommand;
import com.github.talktoissue.commands.DriftCommand;
import com.github.talktoissue.commands.PipelineCommand;
import com.github.talktoissue.commands.RunCommand;
import com.github.talktoissue.commands.ScoreCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;

@Command(
    name = "talk-to-issue",
    mixinStandardHelpOptions = true,
    version = "1.0",
    description = "Analyze meeting transcripts, create GitHub issues, implement solutions, and open PRs — all automatically.",
    subcommands = {
        RunCommand.class,
        CompileCommand.class,
        ScoreCommand.class,
        DriftCommand.class,
        PipelineCommand.class
    }
)
public class App implements Runnable {

    @Option(names = {"-r", "--repo"}, required = true,
            description = "Target GitHub repository (owner/repo format)")
    private String repoFullName;

    @Option(names = {"-w", "--working-dir"},
            description = "Path to the local clone of the target repository")
    private File workingDir;

    @Option(names = {"-m", "--model"}, defaultValue = "gpt-4.1",
            description = "LLM model to use (default: ${DEFAULT-VALUE})")
    private String model;

    @Option(names = {"--dry-run"}, defaultValue = "false",
            description = "Simulate without creating issues or PRs")
    private boolean dryRun;

    public String getRepoFullName() {
        return repoFullName;
    }

    public File getWorkingDir() {
        return workingDir;
    }

    public String getModel() {
        return model;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    @Override
    public void run() {
        System.err.println("Please specify a subcommand: run, compile, score, drift, or pipeline.");
        System.err.println("Use --help for usage information.");
        new CommandLine(this).usage(System.err);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }
}
