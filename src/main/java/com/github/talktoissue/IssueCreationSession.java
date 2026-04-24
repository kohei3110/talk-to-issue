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
import com.github.talktoissue.tools.ListLabelsTool;
import org.kohsuke.github.GHRepository;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class IssueCreationSession {

    private static final String SYSTEM_PROMPT = """
        <rules>
        You are a meeting transcript analyzer. Your job is to:
        1. Read the meeting transcript provided by the user.
        2. Extract all action items, tasks, decisions, and follow-ups.
        3. For each item, use the `create_issue` tool to create a GitHub issue.
        4. Before creating issues, use the `list_labels` tool to see available labels and apply appropriate ones.

        Guidelines for creating issues:
        - Title: concise, actionable summary (e.g., "Implement user authentication API")
        - Body should include:
          - Context from the discussion (who proposed it, why it's needed)
          - Clear description of what needs to be done
          - Acceptance criteria if inferable
          - Deadline or priority if mentioned in the transcript
          - Relevant quotes from the transcript
        - Apply labels that match the nature of the task (e.g., "bug", "enhancement", "documentation")
        - Assign to mentioned team members if their GitHub usernames can be inferred

        Important:
        - Create separate issues for each distinct action item — do NOT combine multiple tasks into one issue.
        - Skip general discussion points that don't have a clear action or deliverable.
        - Use the language that matches the transcript language (e.g., if the transcript is in Japanese, write the issue in Japanese).
        </rules>
        """;

    private final CopilotClient client;
    private final String model;
    private final GHRepository repository;
    private final boolean dryRun;

    public IssueCreationSession(CopilotClient client, String model, GHRepository repository, boolean dryRun) {
        this.client = client;
        this.model = model;
        this.repository = repository;
        this.dryRun = dryRun;
    }

    public List<CreateIssueTool.CreatedIssue> run(String transcript) throws Exception {
        var createIssueTool = new CreateIssueTool(repository, dryRun);
        var listLabelsTool = new ListLabelsTool(repository);

        var session = client.createSession(
            new SessionConfig()
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setModel(model)
                .setTools(List.of(createIssueTool.build(), listLabelsTool.build()))
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
            以下の会議トランスクリプトを解析し、アクションアイテム・タスク・決定事項を抽出してください。
            各アイテムについて create_issue ツールを使ってGitHub Issueを作成してください。
            まず list_labels でリポジトリの既存ラベルを確認してから、適切なラベルを選んでください。

            <transcript>
            %s
            </transcript>
            """.formatted(transcript);

        session.send(new MessageOptions().setPrompt(prompt)).get();
        done.get();
        session.close();

        return createIssueTool.getCreatedIssues();
    }
}
