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
import com.github.talktoissue.tools.GenerateSlideTool;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SelfIntroSession {

    private static final String SYSTEM_PROMPT = """
        <rules>
        You are a technical presentation creator for developer conferences. Your job is to:
        1. Read the detailed project context provided by the user.
        2. Create a comprehensive slide deck that explains the application's architecture, purpose, and significance.
        3. Use the `generate_slide` tool to produce the PowerPoint file.
        4. Actively use diagrams (layout: "flow" and "grid") to visually explain architecture and workflows.

        The generate_slide tool supports 3 slide layouts:
        - "bullets": traditional title + bullet points (use for explanatory text)
        - "flow": horizontal flow diagram with boxes and arrows (use for pipelines, workflows, sequences)
        - "grid": grid of boxes (use for showing components, features, categories)

        For "flow" and "grid" layouts, use diagram_items (array of {label, detail}) instead of bullets.
        Keep diagram labels short (2-5 words). Use detail for 1-line supplementary info.

        Guidelines for creating slides:
        - Create 12-15 slides covering these topics in order:
          1. [bullets] Title slide: tool name ("talk-to-issue"), tagline
          2. [bullets] Problem statement: 手動でのIssue作成の課題、コンテキストの散逸、実装までの距離
          3. [bullets] Solution overview: what talk-to-issue does end-to-end (3-5 detailed bullets)
          4. [bullets] GitHub Copilot SDK for Java とは: SDK概要、セッション・ツール・イベントモデル、Java 17+、CompletableFuture 非同期
          5. [bullets] SDK活用パターン: SessionConfig, SystemMessage, ToolDefinition, event-driven completion の詳細
          6. [grid] エージェント構成: 10個のセッション (IssueCompiler, IssueCreation, QualityScorer, IssueRefine, Implementation, Verification, DriftDetector, CodeReview, TranscriptFetch, SelfIntro)
          7. [flow] パイプライン全体フロー: Context集約 → Compile → Score → Refine → Implement → Verify → Drift検出 → CodeReview
          8. [grid] ツールエコシステム: GitHub操作(create_issue等), コード操作(read_file等), レポート(report_quality_score等), その他(generate_slide等) をカテゴリ別に
          9. [flow] コンテキスト集約: File → ContextAggregator, GitHub → ContextAggregator, WorkIQ → ContextAggregator, MCP → ContextAggregator
          10. [flow] サーバーモード: Webhook受信 → EventRouter → WorkQueue → Session実行 → 結果
          11. [bullets] Score & Refine ループ: 6次元品質スコアリング (明確性, 具体性, 受入基準, スコープ, テスト性, コンテキスト)、自動改善、閾値ベース再試行
          12. [bullets] Implement & Verify ループ: コード生成、ブランチ作成、ビルド・テスト自動検証、エラー自動修正(最大3回)
          13. [bullets] Intent Drift & Code Review: PR差分とIssue要件の乖離検出 (scope_creep, missing_requirement等)、セルフコードレビュー
          14. [bullets] デモ・利用例: CLI コマンド例、サーバーモード起動例
          15. [bullets] まとめ・今後の展望: 意義、拡張性、GitHub Copilot SDK for Java のポテンシャル

        Content guidelines:
        - Each bullet point should be detailed (10-20 words) and informative — this is a deep-dive technical talk
        - Use Japanese for all slide content
        - The output filename should be "talk-to-issue-overview.pptx"
        - Include specific class names, tool names, and technical details in the slides
        - For flow diagrams: 4-7 items work best
        - For grid diagrams: 4-9 items work best
        </rules>
        """;

    private final CopilotClient client;
    private final String model;
    private final File outputDir;
    private final boolean dryRun;

    public SelfIntroSession(CopilotClient client, String model, File outputDir, boolean dryRun) {
        this.client = client;
        this.model = model;
        this.outputDir = outputDir;
        this.dryRun = dryRun;
    }

    public String run(String context) throws Exception {
        var slideTool = new GenerateSlideTool(outputDir, dryRun);

        var session = client.createSession(
            new SessionConfig()
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setModel(model)
                .setTools(List.of(slideTool.build()))
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
            以下のプロジェクト情報をもとに、このツールの役割を説明する自己紹介スライドを作成してください。
            generate_slide ツールを使ってPowerPointファイルを生成してください。

            <project_context>
            %s
            </project_context>
            """.formatted(context);

        session.send(new MessageOptions().setPrompt(prompt)).get();
        done.get();
        session.close();

        return slideTool.getGeneratedFilePath();
    }
}
