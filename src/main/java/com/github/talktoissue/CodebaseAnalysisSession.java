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
import com.github.talktoissue.tools.ExecuteCommandTool;
import com.github.talktoissue.tools.ListDirectoryTool;
import com.github.talktoissue.tools.ReadFileTool;
import com.github.talktoissue.tools.ReportDiscoveryTool;
import com.github.talktoissue.tools.SearchCodeTool;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Autonomously analyzes a codebase to discover improvement opportunities.
 * Scans for TODO/FIXME comments, test gaps, security issues, tech debt,
 * error handling gaps, and documentation issues.
 */
public class CodebaseAnalysisSession {

    private static final String SYSTEM_PROMPT = """
        <rules>
        You are an autonomous codebase analyst. Your job is to:
        1. Systematically scan the codebase to discover improvement opportunities.
        2. Use the provided tools to explore the code structure and content.
        3. Report all findings using the `report_discovery` tool.

        IMPORTANT: Do NOT use built-in filesystem tools (glob, view, grep). Use the custom tools
        listed above instead: list_dir, read_file, search_code, execute_command.

        ## Analysis Categories

        Analyze the codebase for improvements in these categories:

        ### 1. TODO/FIXME/HACK (category: "todo")
        - Search for TODO, FIXME, HACK, XXX, WORKAROUND comments
        - Each found comment is a potential improvement

        ### 2. Test Coverage Gaps (category: "test_gap")
        - Compare source files in src/main with test files in src/test
        - Identify classes/modules without corresponding test files
        - Check for low-coverage patterns (empty test classes, few test methods)

        ### 3. Security Issues (category: "security")
        - Hardcoded secrets, API keys, passwords
        - Missing input validation at system boundaries
        - Unsafe deserialization or injection vulnerabilities
        - Insufficient access controls

        ### 4. Technical Debt (category: "tech_debt")
        - Duplicated code blocks
        - Excessively long methods (>50 lines)
        - Deprecated API usage
        - Overly complex class hierarchies
        - Missing or inconsistent error handling patterns

        ### 5. Error Handling (category: "error_handling")
        - Empty catch blocks
        - Catch-all Exception handlers without proper logging
        - Missing null checks at boundaries
        - Swallowed exceptions

        ### 6. Documentation (category: "documentation")
        - Public APIs without Javadoc
        - Missing README sections
        - Outdated or inaccurate comments

        ## Analysis Strategy

        1. Start with `list_dir` on the root to understand project structure
        2. Use `search_code` to efficiently find patterns (TODO, FIXME, catch blocks, etc.)
        3. Use `read_file` to examine specific files in detail
        4. Optionally use `execute_command` for analysis commands (e.g., line counts)
        5. Call `report_discovery` ONCE with ALL findings

        ## Guidelines
        - Be thorough but practical — focus on actionable improvements
        - Each discovery should be a single, implementable task
        - Provide specific file paths and line references where possible
        - Estimate effort realistically: small (<1h), medium (1-4h), large (>4h)
        - Prioritize high-severity findings (security, data loss risk) over nice-to-haves
        </rules>
        """;

    private final CopilotClient client;
    private final String model;
    private final File workingDir;
    private final List<String> categories;

    public CodebaseAnalysisSession(CopilotClient client, String model, File workingDir,
                                    List<String> categories) {
        this.client = client;
        this.model = model;
        this.workingDir = workingDir;
        this.categories = categories;
    }

    public List<ReportDiscoveryTool.Discovery> run() throws Exception {
        var reportTool = new ReportDiscoveryTool();
        var readFileTool = new ReadFileTool(workingDir);
        var listDirTool = new ListDirectoryTool(workingDir);
        var searchCodeTool = new SearchCodeTool(workingDir);
        var executeCommandTool = new ExecuteCommandTool(workingDir, true); // read-only analysis

        var session = client.createSession(
            new SessionConfig()
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setModel(model)
                .setWorkingDirectory(workingDir.getAbsolutePath())
                .setTools(List.of(
                    reportTool.build(),
                    readFileTool.build(),
                    listDirTool.build(),
                    searchCodeTool.build(),
                    executeCommandTool.build()
                ))
                .setSystemMessage(new SystemMessageConfig()
                    .setMode(SystemMessageMode.APPEND)
                    .setContent(SYSTEM_PROMPT))
        ).get();

        var done = new CompletableFuture<Void>();

        session.on(AssistantMessageEvent.class, msg ->
            System.out.println("[CodebaseAnalysis] " + msg.getData().content()));
        session.on(SessionErrorEvent.class, err ->
            done.completeExceptionally(new RuntimeException(err.getData().message())));
        session.on(SessionIdleEvent.class, idle ->
            done.complete(null));

        String categoryFilter = categories != null && !categories.isEmpty()
            ? "分析対象カテゴリ: " + String.join(", ", categories) + "\n上記のカテゴリのみ分析してください。"
            : "全カテゴリ (todo, test_gap, security, tech_debt, error_handling, documentation) を分析してください。";

        String prompt = """
            このコードベースを自律的に分析し、改善機会を発見してください。

            %s

            手順:
            1. list_dir でプロジェクト構造を把握
            2. search_code でパターン検索 (TODO, FIXME, catch, etc.)
            3. read_file で詳細確認
            4. 全ての発見を report_discovery ツールで一括報告
            """.formatted(categoryFilter);

        session.send(new MessageOptions().setPrompt(prompt)).get();
        done.get();
        session.close();

        var discoveries = reportTool.getDiscoveries();
        return discoveries != null ? discoveries : List.of();
    }
}
