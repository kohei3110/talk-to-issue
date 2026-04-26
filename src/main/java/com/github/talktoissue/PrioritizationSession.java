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
import com.github.talktoissue.tools.ReportDiscoveryTool;
import com.github.talktoissue.tools.ReportPrioritizationTool;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Evaluates discovered improvements and selects the top N based on prioritization criteria.
 */
public class PrioritizationSession {

    private static final String SYSTEM_PROMPT = """
        <rules>
        You are a technical prioritization expert. Your job is to evaluate a list of discovered
        codebase improvement opportunities and select the most impactful ones to implement.

        ## Prioritization Criteria (Weighted)

        1. **Impact (40%)** — How much does this improve code quality, reliability, or developer experience?
        2. **Safety (30%)** — How low-risk is this change? Will it break existing functionality?
        3. **Effort (20%)** — Smaller effort items that deliver value quickly should be preferred.
        4. **Dependencies (10%)** — Can this be done independently, or does it depend on other changes?

        ## Rules
        - Evaluate EVERY discovery against all 4 criteria
        - Select the top items based on overall weighted score
        - Provide a clear rationale for each selected item
        - Prefer high-impact, low-risk, small-effort items
        - Report your selection via the `report_prioritization` tool
        </rules>
        """;

    private final CopilotClient client;
    private final String model;

    public PrioritizationSession(CopilotClient client, String model) {
        this.client = client;
        this.model = model;
    }

    public List<ReportPrioritizationTool.PrioritizedItem> run(
            List<ReportDiscoveryTool.Discovery> discoveries, int maxItems) throws Exception {

        var prioritizationTool = new ReportPrioritizationTool();

        var session = client.createSession(
            new SessionConfig()
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setModel(model)
                .setTools(List.of(prioritizationTool.build()))
                .setSystemMessage(new SystemMessageConfig()
                    .setMode(SystemMessageMode.APPEND)
                    .setContent(SYSTEM_PROMPT))
        ).get();

        var done = new CompletableFuture<Void>();

        session.on(AssistantMessageEvent.class, msg -> {});
        session.on(SessionErrorEvent.class, err -> {
            System.err.println("[Prioritization] Error: " + err.getData().message());
            done.complete(null);
        });
        session.on(SessionIdleEvent.class, idle -> done.complete(null));

        var sb = new StringBuilder();
        sb.append("以下の改善候補から、最大 ").append(maxItems).append(" 件を選んで優先順位を付けてください。\n\n");
        sb.append("## 発見された改善候補\n\n");
        for (int i = 0; i < discoveries.size(); i++) {
            var d = discoveries.get(i);
            sb.append("### ").append(i + 1).append(". ").append(d.title()).append("\n");
            sb.append("- **カテゴリ:** ").append(d.category()).append("\n");
            sb.append("- **重要度:** ").append(d.severity()).append("\n");
            sb.append("- **説明:** ").append(d.description()).append("\n");
            sb.append("- **対象ファイル:** ").append(String.join(", ", d.affectedFiles())).append("\n");
            sb.append("- **工数見積:** ").append(d.estimatedEffort()).append("\n\n");
        }
        sb.append("report_prioritization ツールで選定結果を報告してください。");

        session.send(new MessageOptions().setPrompt(sb.toString())).get();
        done.get();
        session.close();

        return prioritizationTool.getSelectedItems();
    }
}
