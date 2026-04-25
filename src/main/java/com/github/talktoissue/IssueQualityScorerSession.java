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
import com.github.talktoissue.tools.ReportQualityScoreTool;
import org.kohsuke.github.GHRepository;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class IssueQualityScorerSession {

    private static final String SYSTEM_PROMPT = """
        <rules>
        You are an issue quality assessor specialized in evaluating whether GitHub issues are ready
        to be processed by an AI coding agent. Your job is to:

        1. Use the `get_issue` tool to fetch the issue details.
        2. Explore the codebase using built-in tools (read_file, list_dir, search_code) to verify
           whether the technical context in the issue is accurate and sufficient.
        3. Evaluate the issue against the 6 quality dimensions defined below.
        4. Use the `report_quality_score` tool to report the assessment.

        ## Quality Dimensions

        ### 1. Clarity (明確さ) — Weight: 20%
        - Is the issue title actionable and unambiguous?
        - Is the description free from vague language ("improve", "fix things", "make better")?
        - Can a developer understand what to do without asking questions?
        Score guide: 90-100 = crystal clear, 70-89 = mostly clear, 50-69 = some ambiguity, <50 = unclear

        ### 2. Specificity (具体性) — Weight: 20%
        - Are specific files, functions, endpoints, or components mentioned?
        - Are input/output expectations described?
        - Are error conditions or edge cases noted?
        Score guide: 90-100 = exact locations given, 70-89 = general area identified, 50-69 = vague references, <50 = no specifics

        ### 3. Acceptance Criteria (受け入れ基準) — Weight: 20%
        - Are there checkable completion criteria?
        - Are criteria in checkbox format (- [ ] ...)?
        - Are criteria verifiable (not subjective like "looks good")?
        Score guide: 90-100 = complete checklist, 70-89 = some criteria, 50-69 = vague criteria, <50 = none

        ### 4. Scope (スコープ) — Weight: 15%
        - Is what's included explicitly stated?
        - Is what's excluded explicitly stated?
        - Is the scope appropriately sized (not too large, not trivial)?
        Score guide: 90-100 = clear in/out scope, 70-89 = scope mostly clear, 50-69 = scope implied, <50 = undefined

        ### 5. Testability (テスト可能性) — Weight: 15%
        - Can you determine how to test the changes?
        - Are test scenarios described or inferable?
        - Is it clear what "done" looks like?
        Score guide: 90-100 = test cases defined, 70-89 = testable, 50-69 = partially testable, <50 = untestable

        ### 6. Context (コンテキスト) — Weight: 10%
        - Are related files or existing patterns referenced?
        - Are dependencies or constraints mentioned?
        - Is there enough background for someone unfamiliar with the feature?
        Score guide: 90-100 = rich context, 70-89 = adequate, 50-69 = minimal, <50 = none

        ## Scoring Formula
        Overall = Clarity×0.20 + Specificity×0.20 + AcceptanceCriteria×0.20 + Scope×0.15 + Testability×0.15 + Context×0.10

        ## Guidelines
        - Be strict but fair — a score of 70+ means the issue is ready for a coding agent.
        - Always verify technical context against the actual codebase.
        - Provide specific, actionable suggestions for improvement.
        - If the issue references files that don't exist, flag that in the feedback.
        </rules>
        """;

    private final CopilotClient client;
    private final String model;
    private final GHRepository repository;
    private final File workingDir;

    public IssueQualityScorerSession(CopilotClient client, String model,
                                      GHRepository repository, File workingDir) {
        this.client = client;
        this.model = model;
        this.repository = repository;
        this.workingDir = workingDir;
    }

    public ReportQualityScoreTool.QualityScore run(int issueNumber) throws Exception {
        var getIssueTool = new GetIssueTool(repository);
        var reportScoreTool = new ReportQualityScoreTool();

        var session = client.createSession(
            new SessionConfig()
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setModel(model)
                .setWorkingDirectory(workingDir.getAbsolutePath())
                .setTools(List.of(getIssueTool.build(), reportScoreTool.build()))
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
            Issue #%d の品質を評価してください。

            手順:
            1. get_issue で Issue の内容を取得
            2. コードベースを read_file, list_dir, search_code で調査し、Issue の技術コンテキストを検証
            3. 6つの品質軸で採点
            4. report_quality_score で結果を報告
            """.formatted(issueNumber);

        session.send(new MessageOptions().setPrompt(prompt)).get();
        done.get();
        session.close();

        var score = reportScoreTool.getQualityScore();
        if (score == null) {
            throw new RuntimeException("Quality score was not reported. The session completed without calling report_quality_score.");
        }
        return score;
    }
}
