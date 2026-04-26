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
 * Designs a detailed GitHub Issue specification from a prioritized improvement item.
 */
public class SpecDesignSession {

    private static final String SYSTEM_PROMPT = """
        <rules>
        You are a senior software architect designing GitHub Issue specifications for an AI coding agent.
        Your issues must be comprehensive enough that an AI agent can implement them without human guidance.

        ## Issue Body Structure (日本語で記述)

        ### 概要
        1-2文で変更内容を要約。

        ### 背景
        なぜこの変更が必要か。現在の問題点と改善後の期待。

        ### 受入基準
        チェックリスト形式 (- [ ] ...) で具体的・検証可能な基準を列挙。

        ### 技術コンテキスト
        関連ファイル、クラス、メソッド、依存関係を列挙。

        ### 実装ガイド
        推奨する実装アプローチを段階的に記述。

        ### スコープ
        - **含む:** この Issue でやること
        - **含まない:** この Issue でやらないこと

        ### テスト要件
        必要なテストケースと検証方法。

        ## Quality Standards
        - **Clarity:** No vague language. Every statement must be actionable.
        - **Specificity:** Reference exact file paths, class names, method names.
        - **Acceptance Criteria:** At least 3 checkable criteria per issue.
        - **Scope:** Explicitly state what is in-scope and out-of-scope.
        - **Testability:** Define how to verify the change works.
        - **Context:** Reference existing code patterns and conventions.

        ## Rules
        - Explore the codebase with `read_file`, `list_dir`, `search_code` before creating the issue
        - Add the "autonomous" label to every issue
        - Use `list_labels` to check available labels
        - Create the issue using `create_issue`
        - Do NOT use built-in filesystem tools. Use the custom tools provided.
        </rules>
        """;

    private final CopilotClient client;
    private final String model;
    private final GHRepository repository;
    private final File workingDir;
    private final boolean dryRun;

    public SpecDesignSession(CopilotClient client, String model,
                              GHRepository repository, File workingDir, boolean dryRun) {
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

        session.on(AssistantMessageEvent.class, msg -> {});
        session.on(SessionErrorEvent.class, err -> {
            System.err.println("[SpecDesign] Error: " + err.getData().message());
            done.complete(null);
        });
        session.on(SessionIdleEvent.class, idle -> done.complete(null));

        String prompt = """
            以下の改善項目について、GitHub Issue を作成してください。

            ## 改善項目
            - **タイトル:** %s
            - **カテゴリ:** %s
            - **説明:** %s
            - **優先度:** #%d
            - **理由:** %s

            手順:
            1. コードベースを調査して技術コンテキストを把握
            2. list_labels で利用可能なラベルを確認
            3. 詳細な Issue 仕様を設計
            4. create_issue で Issue を作成（"autonomous" ラベルを付与）
            """.formatted(
                item.title(),
                item.category(),
                item.description(),
                item.priorityRank(),
                item.rationale()
            );

        session.send(new MessageOptions().setPrompt(prompt)).get();
        done.get();
        session.close();

        return createIssueTool.getCreatedIssues();
    }
}
