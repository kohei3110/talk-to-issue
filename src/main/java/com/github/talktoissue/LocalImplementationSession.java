package com.github.talktoissue;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.generated.AssistantMessageEvent;
import com.github.copilot.sdk.generated.SessionErrorEvent;
import com.github.copilot.sdk.generated.SessionIdleEvent;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.PermissionHandler;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SessionHooks;
import com.github.copilot.sdk.json.SystemMessageConfig;
import com.github.copilot.sdk.SystemMessageMode;
import com.github.talktoissue.tools.CommitAndPushTool;
import com.github.talktoissue.tools.ExecuteCommandTool;
import com.github.talktoissue.tools.ListDirectoryTool;
import com.github.talktoissue.tools.ReadFileTool;
import com.github.talktoissue.tools.SearchCodeTool;
import com.github.talktoissue.tools.WriteFileTool;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LocalImplementationSession {

    private static final String SYSTEM_PROMPT = """
        <rules>
        You are an autonomous software engineer working on a local codebase. Your job is to:
        1. Read the given improvement task carefully.
        2. Understand the codebase using the custom tools: `list_dir`, `read_file`, `search_code`.
           - Use `list_dir` to explore directory structure.
           - Use `read_file` to read file contents (supports line ranges).
           - Use `search_code` to grep for patterns across the codebase.
        3. Implement the required changes using `write_file` and `execute_command`.
        4. Once implementation is complete:
           a. Use `execute_command` to run build and tests to verify your changes.
           b. Call `commit_and_push` with a descriptive commit message summarizing the improvement.

        IMPORTANT: Do NOT use built-in filesystem tools (glob, view, grep). Use the custom tools
        listed above instead: list_dir, read_file, search_code, write_file, execute_command.

        Guidelines:
        - Write clean, idiomatic code following the project's existing conventions.
        - Make minimal, focused changes — only what's needed to address the improvement task.
        - Test your changes if a test framework is set up (use execute_command to run tests).
        - The commit message should clearly describe the improvement (e.g., "refactor: extract helper method for validation logic").
        </rules>
        """;

    private final CopilotClient client;
    private final String model;
    private final File workingDir;
    private final boolean dryRun;

    public LocalImplementationSession(CopilotClient client, String model,
                                       File workingDir, boolean dryRun) {
        this.client = client;
        this.model = model;
        this.workingDir = workingDir;
        this.dryRun = dryRun;
    }

    public void run(String title, String description) throws Exception {
        var readFileTool = new ReadFileTool(workingDir);
        var listDirTool = new ListDirectoryTool(workingDir);
        var searchCodeTool = new SearchCodeTool(workingDir);
        var writeFileTool = new WriteFileTool(workingDir, dryRun);
        var executeCommandTool = new ExecuteCommandTool(workingDir, dryRun);
        var commitAndPushTool = new CommitAndPushTool(workingDir, dryRun);

        var hooks = new SessionHooks()
            .setOnPostToolUse((input, invocation) -> {
                System.out.println("[DEBUG] Tool: " + input.getToolName() +
                    " | CWD: " + input.getCwd() +
                    " | Result: " + input.getToolResult());
                return CompletableFuture.completedFuture(null);
            });

        System.out.println("[DEBUG] Working directory: " + workingDir.getAbsolutePath());

        var session = client.createSession(
            new SessionConfig()
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setModel(model)
                .setWorkingDirectory(workingDir.getAbsolutePath())
                .setHooks(hooks)
                .setTools(List.of(
                    readFileTool.build(),
                    listDirTool.build(),
                    searchCodeTool.build(),
                    writeFileTool.build(),
                    executeCommandTool.build(),
                    commitAndPushTool.build()
                ))
                .setSystemMessage(new SystemMessageConfig()
                    .setMode(SystemMessageMode.APPEND)
                    .setContent(SYSTEM_PROMPT))
        ).get();

        var done = new CompletableFuture<Void>();

        session.on(AssistantMessageEvent.class, msg ->
            System.out.println("[Copilot] " + msg.getData().content()));
        session.on(SessionErrorEvent.class, err ->
            done.completeExceptionally(new RuntimeException(err.getData().message())));
        session.on(SessionIdleEvent.class, idle ->
            done.complete(null));

        String prompt = """
            以下の改善タスクを実装してください。
            コードベースを理解した上で、必要な変更を加え、テストを実行し、コミットしてください。

            ## タスク: %s

            %s
            """.formatted(title, description);

        session.send(new MessageOptions().setPrompt(prompt)).get();
        done.get();
        session.close();
    }
}
