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
import com.github.talktoissue.tools.GetIssueTool;
import com.github.talktoissue.tools.GetPullRequestDiffTool;
import com.github.talktoissue.tools.ReportDriftTool;
import org.kohsuke.github.GHRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class IntentDriftDetectorSession {

    private static final String SYSTEM_PROMPT = """
        <rules>
        You are an intent drift detector. Your job is to analyze whether a pull request's changes
        align with the original requirements from the linked GitHub issue and (optionally) a meeting transcript.

        ## Procedure
        1. Use `get_pull_request_diff` to fetch the PR metadata and file diffs.
        2. Use `get_issue` to fetch the linked issue. If the issue number is not provided,
           extract it from the PR body (look for "Closes #N", "Fixes #N", or "Resolves #N").
        3. If a meeting transcript is provided, consider it as additional context for the original intent.
        4. Explore the codebase using built-in tools (read_file, list_dir, search_code) to understand
           the context of the changes.
        5. Compare the PR changes against the requirements and report findings using `report_drift`.

        ## Drift Types to Detect

        ### scope_creep
        Changes that go beyond what the issue requested. Examples:
        - Adding features not mentioned in the issue
        - Refactoring unrelated code
        - Modifying files not relevant to the issue

        ### missing_requirement
        Requirements from the issue that are not addressed by the PR. Examples:
        - Acceptance criteria that aren't met
        - Specified files that weren't modified
        - Edge cases mentioned but not handled

        ### approach_divergence
        Implementation differs from the approach specified or implied in the issue. Examples:
        - Using a different library than specified
        - Implementing a different algorithm
        - Placing code in a different location than suggested

        ### unrelated_change
        Changes completely unrelated to the issue. Examples:
        - Formatting changes in unrelated files
        - Dependency updates not mentioned in the issue
        - Configuration changes without justification

        ## Severity Guide
        - **high**: The drift undermines the purpose of the issue or introduces risk
        - **medium**: The drift is notable and should be reviewed, but doesn't block
        - **low**: Minor deviation that's acceptable but worth noting

        ## Verdict Guide
        - **pass**: No significant drifts. All requirements are met.
        - **warn**: Minor drifts detected but requirements are mostly met. Review recommended.
        - **fail**: Significant drifts or unmet requirements. Changes needed before merge.

        ## Guidelines
        - Be thorough but fair — minor code style differences are not drift.
        - Always check each acceptance criterion from the issue against the actual changes.
        - Consider that some changes may be necessary supporting changes (e.g., imports, configs).
        - If the issue doesn't specify an approach, the implementer has freedom in approach.
        </rules>
        """;

    private final CopilotClient client;
    private final String model;
    private final GHRepository repository;
    private final File workingDir;

    public IntentDriftDetectorSession(CopilotClient client, String model,
                                       GHRepository repository, File workingDir) {
        this.client = client;
        this.model = model;
        this.repository = repository;
        this.workingDir = workingDir;
    }

    public ReportDriftTool.DriftReport run(int prNumber, Integer issueNumber, String transcript) throws Exception {
        var getIssueTool = new GetIssueTool(repository);
        var getPRDiffTool = new GetPullRequestDiffTool(repository);
        var reportDriftTool = new ReportDriftTool();

        var tools = new ArrayList<>(List.of(
            getIssueTool.build(),
            getPRDiffTool.build(),
            reportDriftTool.build()
        ));

        var session = client.createSession(
            new SessionConfig()
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setModel(model)
                .setWorkingDirectory(workingDir.getAbsolutePath())
                .setTools(tools)
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
        promptBuilder.append("PR #%d のドリフト分析を実行してください。\n\n".formatted(prNumber));

        promptBuilder.append("手順:\n");
        promptBuilder.append("1. get_pull_request_diff で PR の変更内容を取得\n");

        if (issueNumber != null) {
            promptBuilder.append("2. get_issue で Issue #%d の要件を取得\n".formatted(issueNumber));
        } else {
            promptBuilder.append("2. PR body から関連 Issue 番号を推定し、get_issue で取得\n");
        }

        promptBuilder.append("3. コードベースを調査して変更のコンテキストを把握\n");
        promptBuilder.append("4. 変更内容と要件を突き合わせてドリフトを検出\n");
        promptBuilder.append("5. report_drift で分析結果を報告\n");

        if (transcript != null && !transcript.isBlank()) {
            promptBuilder.append("\n## 参考: 元の会議トランスクリプト\n\n<transcript>\n");
            promptBuilder.append(transcript);
            promptBuilder.append("\n</transcript>\n");
        }

        session.send(new MessageOptions().setPrompt(promptBuilder.toString())).get();
        done.get();
        session.close();

        var report = reportDriftTool.getDriftReport();
        if (report == null) {
            throw new RuntimeException("Drift report was not generated. The session completed without calling report_drift.");
        }
        return report;
    }
}
