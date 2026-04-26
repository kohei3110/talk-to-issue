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
 * Evaluates discovered improvement opportunities and selects the top N items
 * to implement, based on impact, risk, effort, and dependencies.
 */
public class PrioritizationSession {

    private static final String SYSTEM_PROMPT = """
        <rules>
        You are a technical prioritization expert. Your job is to:
        1. Evaluate a list of discovered codebase improvement opportunities.
        2. Rank them by priority considering multiple factors.
        3. Select the top N items for implementation.
        4. Report your selection using the `report_prioritization` tool.

        ## Prioritization Criteria

        Evaluate each discovery on these dimensions:

        ### Impact (40% weight)
        - User-facing impact: Does this affect end users or only developers?
        - Code quality: How much does fixing this improve maintainability?
        - Risk reduction: Does this prevent potential bugs or outages?

        ### Safety (30% weight)
        - Change scope: How isolated is the change? Fewer affected files = safer.
        - Regression risk: Could this fix break other functionality?
        - Reversibility: How easy is it to revert if something goes wrong?

        ### Effort (20% weight)
        - Implementation complexity: How straightforward is the fix?
        - Testing requirements: Does this need new tests or just modifications?
        - Alignment with estimated_effort from discovery

        ### Dependencies (10% weight)
        - Does this need to be done before/after other items?
        - Are there shared files that would cause merge conflicts?

        ## Selection Rules
        - Select at most the requested number of items (maxItems)
        - Prefer high-severity items over low-severity ones
        - Prefer small-effort items when impact is similar (quick wins)
        - Avoid selecting items that modify the same files (reduce conflict risk)
        - Security issues should generally be prioritized highest

        ## Output
        Call `report_prioritization` with the selected items, ordered by priority_rank (1 = highest).
        Include a clear rationale for each selection.
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

        var reportTool = new ReportPrioritizationTool();

        var session = client.createSession(
            new SessionConfig()
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setModel(model)
                .setTools(List.of(reportTool.build()))
                .setSystemMessage(new SystemMessageConfig()
                    .setMode(SystemMessageMode.APPEND)
                    .setContent(SYSTEM_PROMPT))
        ).get();

        var done = new CompletableFuture<Void>();

        session.on(AssistantMessageEvent.class, msg ->
            System.out.println("[Prioritization] " + msg.getData().content()));
        session.on(SessionErrorEvent.class, err ->
            done.completeExceptionally(new RuntimeException(err.getData().message())));
        session.on(SessionIdleEvent.class, idle ->
            done.complete(null));

        // Format discoveries as structured input
        var sb = new StringBuilder();
        sb.append("以下の ").append(discoveries.size()).append(" 件の改善機会から、上位 ")
          .append(maxItems).append(" 件を選択し、優先順位を付けてください。\n\n");

        for (int i = 0; i < discoveries.size(); i++) {
            var d = discoveries.get(i);
            sb.append("### Discovery #").append(i + 1).append("\n");
            sb.append("- **カテゴリ**: ").append(d.category()).append("\n");
            sb.append("- **タイトル**: ").append(d.title()).append("\n");
            sb.append("- **説明**: ").append(d.description()).append("\n");
            sb.append("- **重要度**: ").append(d.severity()).append("\n");
            sb.append("- **影響ファイル**: ").append(String.join(", ", d.affectedFiles())).append("\n");
            sb.append("- **想定工数**: ").append(d.estimatedEffort()).append("\n\n");
        }

        sb.append("report_prioritization ツールで、選択した上位 ").append(maxItems).append(" 件を報告してください。");

        session.send(new MessageOptions().setPrompt(sb.toString())).get();
        done.get();
        session.close();

        var items = reportTool.getSelectedItems();
        return items != null ? items : List.of();
    }
}
