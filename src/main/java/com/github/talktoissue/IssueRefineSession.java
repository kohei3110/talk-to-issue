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
import com.github.talktoissue.tools.ListDirectoryTool;
import com.github.talktoissue.tools.ReadFileTool;
import com.github.talktoissue.tools.ReportQualityScoreTool;
import com.github.talktoissue.tools.SearchCodeTool;
import com.github.talktoissue.tools.UpdateIssueTool;
import org.kohsuke.github.GHRepository;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Automatically refines issues that scored below the quality threshold.
 * Explores the codebase to add specific file references, acceptance criteria,
 * and technical context, then updates the issue and re-scores.
 */
public class IssueRefineSession {

    private static final String SYSTEM_PROMPT = """
        <rules>
        You are an issue refinement agent. Your job is to improve a GitHub issue's quality
        so that it can be processed by an AI coding agent.

        You will be given:
        - The issue number
        - The quality score feedback (which dimensions scored low and why)

        ## Procedure
        1. Use `get_issue` to fetch the current issue content.
        2. Analyze the quality feedback to understand what needs improvement.
        3. Explore the codebase using `list_dir`, `read_file`, and `search_code` to gather
           the specific technical context needed.
        4. Rewrite the issue body to address the low-scoring dimensions:

        ### Improving Clarity
        - Replace vague language with specific technical descriptions
        - Add concrete expected behavior and current behavior

        ### Improving Specificity
        - Add specific file paths that need to be modified
        - Add function/class names that are involved
        - Include code snippets showing current behavior

        ### Improving Acceptance Criteria
        - Add checkbox-formatted criteria (- [ ] ...)
        - Make each criterion independently verifiable
        - Include both functional and technical criteria

        ### Improving Scope
        - Add explicit "In scope" and "Out of scope" sections
        - List files that should be modified
        - List files that should NOT be modified

        ### Improving Testability
        - Add specific test scenarios
        - Include expected inputs and outputs
        - Reference existing test files and patterns

        ### Improving Context
        - Reference related existing code patterns
        - List dependencies and constraints
        - Add links to related issues or documentation

        5. Use `update_issue` to save the improved issue body.
           IMPORTANT: Preserve the original intent. Only ADD specificity and context.
           Do NOT change what the issue is asking for.

        ## Important
        - Do NOT use built-in filesystem tools (glob, view, grep). Use the custom tools instead.
        - The improved issue should be ready for a coding agent (score ≥ 70).
        - Always include a technical context section with actual file paths from the codebase.
        - Use the project's language for the issue (Japanese if the original was Japanese).
        </rules>
        """;

    private final CopilotClient client;
    private final String model;
    private final GHRepository repository;
    private final File workingDir;
    private final boolean dryRun;

    public IssueRefineSession(CopilotClient client, String model, GHRepository repository,
                               File workingDir, boolean dryRun) {
        this.client = client;
        this.model = model;
        this.repository = repository;
        this.workingDir = workingDir;
        this.dryRun = dryRun;
    }

    /**
     * Refine an issue based on quality score feedback.
     *
     * @param issueNumber the issue to refine
     * @param scoreFeedback structured feedback from the quality scorer
     */
    public void run(int issueNumber, ReportQualityScoreTool.QualityScore scoreFeedback) throws Exception {
        var getIssueTool = new GetIssueTool(repository);
        var updateIssueTool = new UpdateIssueTool(repository, dryRun);
        var readFileTool = new ReadFileTool(workingDir);
        var listDirTool = new ListDirectoryTool(workingDir);
        var searchCodeTool = new SearchCodeTool(workingDir);

        var session = client.createSession(
            new SessionConfig()
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setModel(model)
                .setWorkingDirectory(workingDir.getAbsolutePath())
                .setTools(List.of(
                    getIssueTool.build(),
                    updateIssueTool.build(),
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

        var feedbackBuilder = new StringBuilder();
        feedbackBuilder.append("Issue #%d の品質を改善してください。\n\n".formatted(issueNumber));
        feedbackBuilder.append("## 現在のスコア: %d/100\n\n".formatted(scoreFeedback.overallScore()));

        feedbackBuilder.append("### 各次元のスコアとフィードバック\n");
        for (var dim : scoreFeedback.dimensions()) {
            String status = dim.score() >= 70 ? "✓" : "✗ (改善必要)";
            feedbackBuilder.append("- **%s**: %d/100 %s — %s\n".formatted(
                dim.name(), dim.score(), status, dim.feedback()));
        }

        if (!scoreFeedback.suggestions().isEmpty()) {
            feedbackBuilder.append("\n### 改善提案\n");
            for (var s : scoreFeedback.suggestions()) {
                feedbackBuilder.append("- %s\n".formatted(s));
            }
        }

        feedbackBuilder.append("""

            手順:
            1. get_issue で現在の Issue 内容を取得
            2. 低スコアの次元を重点的にコードベースを調査（list_dir, read_file, search_code）
            3. 具体的なファイルパス、関数名、コードパターンを特定
            4. Issue の本文を改善して update_issue で保存
            """);

        session.send(new MessageOptions().setPrompt(feedbackBuilder.toString())).get();
        done.get();
        session.close();
    }
}
