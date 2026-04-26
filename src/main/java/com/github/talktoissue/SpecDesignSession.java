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
import com.github.talktoissue.tools.ReportPrioritizationTool;
import com.github.talktoissue.tools.SearchCodeTool;
import org.kohsuke.github.GHRepository;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Designs detailed GitHub Issue specifications from prioritized improvement items.
 * Explores the codebase to gather technical context and creates high-quality,
 * coding-agent-ready issues that meet the 6-dimension quality criteria.
 */
public class SpecDesignSession {

    private static final String SYSTEM_PROMPT = """
        <rules>
        You are an autonomous specification designer. Your job is to:
        1. Take a prioritized improvement item and design a detailed GitHub Issue specification.
        2. Explore the codebase to gather specific technical context.
        3. Create a high-quality, coding-agent-ready Issue using `create_issue`.

        IMPORTANT: Do NOT use built-in filesystem tools (glob, view, grep). Use the custom tools
        listed above instead: list_dir, read_file, search_code.

        ## Issue Quality Standards (6 Dimensions)

        Your issue MUST score high on all 6 quality dimensions:

        1. **明確性 (Clarity)**: The issue clearly states what needs to be done. No ambiguity.
        2. **具体性 (Specificity)**: References specific files, functions, and line ranges.
        3. **受入基準 (Acceptance Criteria)**: Each criterion is verifiable and testable.
        4. **スコープ (Scope)**: Clear boundaries — what IS and IS NOT included.
        5. **テスト性 (Testability)**: Describes how to verify the fix (unit tests, integration tests, etc.).
        6. **コンテキスト (Context)**: Includes relevant code references, patterns, and dependencies.

        ## Issue Body Structure

        ```markdown
        ## 概要
        [1-2 sentence summary of what needs to be done and why]

        ## 背景
        [Why this improvement is needed — the problem or risk it addresses]

        ## 受け入れ基準
        - [ ] [Specific, verifiable criterion 1]
        - [ ] [Specific, verifiable criterion 2]
        - [ ] [Specific, verifiable criterion 3]

        ## 技術コンテキスト
        - **関連ファイル**: [List specific files that need modification]
        - **既存パターン**: [Describe existing patterns to follow]
        - **依存関係**: [Dependencies, libraries, or APIs involved]

        ## 実装ガイド
        [Step-by-step guidance for the implementing agent]
        1. [First step with specific file/function references]
        2. [Second step]
        3. [...]

        ## スコープ
        - **含む**: [What is in scope]
        - **含まない**: [What is explicitly out of scope]

        ## テスト要件
        - [Specific test cases to add/modify]
        - [Expected test command to verify]
        ```

        ## Workflow
        1. Use `list_labels` to find appropriate labels
        2. Use `list_dir`, `read_file`, `search_code` to explore the codebase
        3. Design the specification with deep technical context
        4. Use `create_issue` to create the issue with label "autonomous"

        ## Guidelines
        - Each issue is for ONE specific improvement — focused and implementable
        - Always add the label "autonomous" to mark AI-generated issues
        - Use Japanese for issue content
        - Acceptance criteria must be specific enough for automated verification
        - Implementation guide should reference actual code patterns found in the codebase
        </rules>
        """;

    private final CopilotClient client;
    private final String model;
    private final GHRepository repository;
    private final File workingDir;
    private final boolean dryRun;

    public SpecDesignSession(CopilotClient client, String model, GHRepository repository,
                              File workingDir, boolean dryRun) {
        this.client = client;
        this.model = model;
        this.repository = repository;
        this.workingDir = workingDir;
        this.dryRun = dryRun;
    }

    public List<CreateIssueTool.CreatedIssue> run(ReportPrioritizationTool.PrioritizedItem item) throws Exception {
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
            System.out.println("[SpecDesign] " + msg.getData().content()));
        session.on(SessionErrorEvent.class, err ->
            done.completeExceptionally(new RuntimeException(err.getData().message())));
        session.on(SessionIdleEvent.class, idle ->
            done.complete(null));

        String prompt = """
            以下の改善項目について、コードベースを調査した上で、高品質なGitHub Issue仕様を設計・作成してください。

            ## 改善項目
            - **タイトル**: %s
            - **カテゴリ**: %s
            - **説明**: %s
            - **優先度根拠**: %s

            手順:
            1. list_labels で利用可能なラベルを確認
            2. コードベースを調査（list_dir, read_file, search_code）して技術コンテキストを収集
            3. 6次元品質基準を満たす詳細な Issue を create_issue で作成
               - 必ず "autonomous" ラベルを付与してください
            """.formatted(
                item.title(),
                item.category(),
                item.description(),
                item.rationale()
            );

        session.send(new MessageOptions().setPrompt(prompt)).get();
        done.get();
        session.close();

        return createIssueTool.getCreatedIssues();
    }
}
