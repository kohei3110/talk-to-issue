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
import com.github.talktoissue.tools.GetPullRequestDiffTool;
import com.github.talktoissue.tools.ListDirectoryTool;
import com.github.talktoissue.tools.ReadFileTool;
import com.github.talktoissue.tools.ReportCodeReviewTool;
import com.github.talktoissue.tools.SearchCodeTool;
import org.kohsuke.github.GHRepository;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Self code review session: reviews a PR diff from a reviewer's perspective,
 * checking for security vulnerabilities, bugs, edge cases, performance, and style issues.
 */
public class CodeReviewSession {

    private static final String SYSTEM_PROMPT = """
        <rules>
        You are a senior code reviewer. Your job is to review a pull request's changes
        from a quality and correctness perspective. This is NOT about whether the changes
        match the requirements (that's drift detection) — this is about whether the code
        itself is well-written, secure, and correct.

        ## Procedure
        1. Use `get_pull_request_diff` to fetch the PR changes.
        2. For each changed file, use `read_file` to see the full context around the changes.
        3. Use `search_code` to check for similar patterns, related test files, and usage sites.
        4. Evaluate the changes against the review criteria below.
        5. Report your findings using `report_code_review`.

        ## Review Criteria

        ### Security (critical)
        - SQL injection, XSS, path traversal, command injection
        - Hardcoded credentials or secrets
        - Missing input validation at system boundaries
        - Insecure deserialization
        - Missing authentication/authorization checks

        ### Bugs (critical)
        - Null pointer dereferences
        - Off-by-one errors
        - Resource leaks (unclosed streams, connections)
        - Race conditions in concurrent code
        - Incorrect error handling (swallowed exceptions, wrong exception types)

        ### Edge Cases (warning)
        - Empty collections, null inputs, boundary values
        - Unicode/encoding issues
        - Large input handling
        - Timeout and retry behavior

        ### Performance (warning)
        - N+1 query patterns
        - Unnecessary object allocations in hot paths
        - Missing pagination for large result sets
        - Blocking calls in async contexts

        ### Error Handling (warning)
        - Missing error cases
        - Generic catch blocks that hide errors
        - Missing cleanup in error paths

        ### Style & Maintainability (suggestion)
        - Code duplication
        - Overly complex methods (too many branches, too long)
        - Naming that doesn't match conventions
        - Missing logging for important operations

        ## Important
        - Do NOT use built-in filesystem tools. Use the custom tools instead.
        - Focus on real issues, not nitpicks.
        - Every finding MUST have a concrete suggestion for how to fix it.
        - If the code is good, say so — don't invent issues.
        - Be fair: if the approach is valid but different from what you'd choose, note it as a suggestion, not a bug.
        </rules>
        """;

    private final CopilotClient client;
    private final String model;
    private final GHRepository repository;
    private final File workingDir;

    public CodeReviewSession(CopilotClient client, String model,
                              GHRepository repository, File workingDir) {
        this.client = client;
        this.model = model;
        this.repository = repository;
        this.workingDir = workingDir;
    }

    public ReportCodeReviewTool.CodeReview run(int prNumber) throws Exception {
        var getPRDiffTool = new GetPullRequestDiffTool(repository);
        var reportReviewTool = new ReportCodeReviewTool();
        var readFileTool = new ReadFileTool(workingDir);
        var listDirTool = new ListDirectoryTool(workingDir);
        var searchCodeTool = new SearchCodeTool(workingDir);

        var session = client.createSession(
            new SessionConfig()
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setModel(model)
                .setWorkingDirectory(workingDir.getAbsolutePath())
                .setTools(List.of(
                    getPRDiffTool.build(),
                    reportReviewTool.build(),
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
            PR #%d のコードレビューを実行してください。

            手順:
            1. get_pull_request_diff で PR の変更内容を取得
            2. 各変更ファイルのコンテキストを read_file で確認
            3. search_code で関連パターンやテストファイルを確認
            4. セキュリティ、バグ、エッジケース、パフォーマンス、エラーハンドリング、スタイルの観点でレビュー
            5. report_code_review で結果を報告
            """.formatted(prNumber);

        session.send(new MessageOptions().setPrompt(prompt)).get();
        done.get();
        session.close();

        var review = reportReviewTool.getCodeReview();
        if (review == null) {
            throw new RuntimeException("Code review was not reported. The session completed without calling report_code_review.");
        }
        return review;
    }
}
