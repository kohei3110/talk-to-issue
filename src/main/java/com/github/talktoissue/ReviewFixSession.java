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
import com.github.talktoissue.tools.CommitAndPushTool;
import com.github.talktoissue.tools.ExecuteCommandTool;
import com.github.talktoissue.tools.GetPullRequestDiffTool;
import com.github.talktoissue.tools.GetPullRequestReviewsTool;
import com.github.talktoissue.tools.ListDirectoryTool;
import com.github.talktoissue.tools.ReadFileTool;
import com.github.talktoissue.tools.SearchCodeTool;
import com.github.talktoissue.tools.WriteFileTool;
import org.kohsuke.github.GHRepository;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Reads PR review comments (from self-review or external reviewers),
 * applies fixes to the code, and commits + pushes the changes.
 * This closes the feedback loop for both human and AI review.
 */
public class ReviewFixSession {

    private static final String SYSTEM_PROMPT = """
        <rules>
        You are an autonomous code fixer. Your job is to read review feedback on a pull request
        and fix the code accordingly, then commit and push the fixes.

        ## Procedure
        1. Use `get_pull_request_reviews` to fetch all review comments and inline feedback.
        2. Use `get_pull_request_diff` to understand the current state of PR changes.
        3. For each review finding:
           a. Use `read_file` to read the affected file.
           b. Understand the reviewer's concern.
           c. Apply the fix using `write_file`.
        4. If the fix is non-trivial, use `search_code` to understand related patterns.
        5. After all fixes are applied, use `execute_command` to run build and tests.
        6. Use `commit_and_push` to push the fixes with a descriptive commit message.

        ## Guidelines
        - Fix ALL review comments, not just some.
        - If a review comment is a suggestion/opinion (not a bug), still apply it unless it would break functionality.
        - If you disagree with a review comment, apply it anyway — the reviewer's judgment takes precedence.
        - After fixing, verify the build still passes before committing.
        - Commit message should reference "Address review feedback" and list what was fixed.
        - Do NOT use built-in filesystem tools. Use the custom tools instead.
        - Make minimal changes — only fix what was flagged.
        </rules>
        """;

    private final CopilotClient client;
    private final String model;
    private final GHRepository repository;
    private final File workingDir;
    private final boolean dryRun;

    public ReviewFixSession(CopilotClient client, String model, GHRepository repository,
                             File workingDir, boolean dryRun) {
        this.client = client;
        this.model = model;
        this.repository = repository;
        this.workingDir = workingDir;
        this.dryRun = dryRun;
    }

    /**
     * Fix code based on PR review comments.
     *
     * @param prNumber the pull request number
     * @param additionalContext optional context to include (e.g., self-review findings serialized as text)
     */
    public void run(int prNumber, String additionalContext) throws Exception {
        var reviewsTool = new GetPullRequestReviewsTool(repository);
        var diffTool = new GetPullRequestDiffTool(repository);
        var readFileTool = new ReadFileTool(workingDir);
        var listDirTool = new ListDirectoryTool(workingDir);
        var searchCodeTool = new SearchCodeTool(workingDir);
        var writeFileTool = new WriteFileTool(workingDir, dryRun);
        var executeCommandTool = new ExecuteCommandTool(workingDir, dryRun);
        var commitAndPushTool = new CommitAndPushTool(workingDir, dryRun);

        var session = client.createSession(
            new SessionConfig()
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setModel(model)
                .setWorkingDirectory(workingDir.getAbsolutePath())
                .setTools(List.of(
                    reviewsTool.build(),
                    diffTool.build(),
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

        var promptBuilder = new StringBuilder();
        promptBuilder.append("PR #%d のレビューコメントに対応してコードを修正してください。\n\n".formatted(prNumber));
        promptBuilder.append("手順:\n");
        promptBuilder.append("1. get_pull_request_reviews でレビューコメントを取得\n");
        promptBuilder.append("2. get_pull_request_diff で現在の変更内容を確認\n");
        promptBuilder.append("3. 各レビュー指摘に対してコードを修正\n");
        promptBuilder.append("4. execute_command でビルド・テストを実行して確認\n");
        promptBuilder.append("5. commit_and_push で修正をプッシュ\n");

        if (additionalContext != null && !additionalContext.isBlank()) {
            promptBuilder.append("\n## 追加コンテキスト（セルフレビュー結果）\n\n");
            promptBuilder.append(additionalContext);
        }

        session.send(new MessageOptions().setPrompt(promptBuilder.toString())).get();
        done.get();
        session.close();
    }
}
