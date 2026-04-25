package com.github.talktoissue;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.generated.AssistantMessageEvent;
import com.github.copilot.sdk.generated.SessionErrorEvent;
import com.github.copilot.sdk.generated.SessionIdleEvent;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.PermissionHandler;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SystemMessageConfig;
import com.github.copilot.sdk.SystemMessageMode;
import com.github.talktoissue.tools.CreateIssueTool;
import com.github.talktoissue.tools.ListDirectoryTool;
import com.github.talktoissue.tools.ListLabelsTool;
import com.github.talktoissue.tools.ReadFileTool;
import com.github.talktoissue.tools.SearchCodeTool;
import org.kohsuke.github.GHRepository;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class IssueCompilerSession {

    private static final String SYSTEM_PROMPT = """
        <rules>
        You are a coding-agent-ready issue compiler. Your job is to:
        1. Read the context provided by the user. It may be a meeting transcript, design document,
           bug report, customer feedback, email thread, or any other source of action items.
        2. Extract all action items, tasks, decisions, and follow-ups.
        3. For each item, explore the codebase using custom tools (`read_file`, `list_dir`, `search_code`)
           to gather technical context — related files, existing patterns, dependencies, and conventions.
           Do NOT use built-in filesystem tools (glob, view, grep). Use the custom tools instead.
        4. Use the `list_labels` tool to see available labels.
        5. Use the `create_issue` tool to create a structured, coding-agent-ready GitHub issue for each item.

        Each issue body MUST follow this exact structure:

        ```markdown
        ## 概要
        [1-2 sentence summary of what needs to be done]

        ## 受け入れ基準
        - [ ] [Specific, verifiable criterion 1]
        - [ ] [Specific, verifiable criterion 2]
        - [ ] [Specific, verifiable criterion 3]

        ## 技術コンテキスト
        - **関連ファイル**: [List files that need to be modified or referenced, found via codebase exploration]
        - **既存パターン**: [Describe existing patterns in the codebase that should be followed]
        - **依存関係**: [Note any dependencies, libraries, or APIs involved]

        ## スコープ
        - **含む**: [What is in scope]
        - **含まない**: [What is explicitly out of scope]

        ## テスト要件
        - [Describe expected tests — unit tests, integration tests, or manual verification steps]
        ```

        Guidelines:
        - ALWAYS explore the codebase before creating issues to provide accurate technical context.
        - Create separate issues for each distinct action item — do NOT combine multiple tasks.
        - Skip discussion points without a clear action or deliverable.
        - Match the context language (e.g., Japanese context → Japanese issue).
        - Apply labels that match the task nature (bug, enhancement, documentation, etc.).
        - The issue title should be concise and actionable.
        - Acceptance criteria must be specific and verifiable — avoid vague criteria like "works correctly".
        - Technical context must reference actual files and patterns found in the codebase.
        </rules>
        """;

    private final CopilotClient client;
    private final String model;
    private final GHRepository repository;
    private final File workingDir;
    private final boolean dryRun;

    public IssueCompilerSession(CopilotClient client, String model, GHRepository repository,
                                 File workingDir, boolean dryRun) {
        this.client = client;
        this.model = model;
        this.repository = repository;
        this.workingDir = workingDir;
        this.dryRun = dryRun;
    }

    public List<CreateIssueTool.CreatedIssue> run(String context) throws Exception {
        var createIssueTool = new CreateIssueTool(repository, dryRun);
        var listLabelsTool = new ListLabelsTool(repository);
        var readFileTool = new ReadFileTool(workingDir);
        var listDirTool = new ListDirectoryTool(workingDir);
        var searchCodeTool = new SearchCodeTool(workingDir);

        var session = client.createSession(
            new SessionConfig()
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setModel(model)
                .setWorkingDirectory(workingDir.getAbsolutePath())
                .setTools(List.of(
                    createIssueTool.build(),
                    listLabelsTool.build(),
                    readFileTool.build(),
                    listDirTool.build(),
                    searchCodeTool.build()
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
            以下のコンテキストを解析し、アクションアイテム・タスク・決定事項を抽出してください。
            各アイテムについて、まずコードベースを調査して技術コンテキストを把握した上で、
            coding-agent-ready な構造化 Issue を作成してください。

            手順:
            1. list_labels でリポジトリの既存ラベルを確認
            2. コンテキストからアクションアイテムを抽出
            3. 各アイテムについて read_file, list_dir, search_code でコードベースを調査
            4. 調査結果を踏まえて create_issue で構造化 Issue を作成

            %s
            """.formatted(context);

        session.send(new MessageOptions().setPrompt(prompt)).get();
        done.get();
        session.close();

        return createIssueTool.getCreatedIssues();
    }
}
