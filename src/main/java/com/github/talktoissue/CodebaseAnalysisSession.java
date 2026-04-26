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
 */
public class CodebaseAnalysisSession {

    private static final String SYSTEM_PROMPT = """
        <rules>
        You are a codebase analyst. Your job is to systematically scan the codebase and identify
        concrete improvement opportunities. You MUST explore the code thoroughly before reporting.

        ## Analysis Categories

        1. **TODO/FIXME** — Search for TODO, FIXME, HACK, XXX comments using `search_code`
        2. **Test Gaps** — Compare source files vs test files. Look for classes without corresponding tests
        3. **Security** — Check for hardcoded secrets, missing input validation, unsafe operations
        4. **Tech Debt** — Find duplicated code, overly complex methods, deprecated API usage
        5. **Error Handling** — Look for empty catch blocks, swallowed exceptions, missing error handling
        6. **Documentation** — Find public APIs without javadoc, missing README sections

        ## Process

        1. Use `list_dir` to understand the project structure
        2. Use `search_code` to find patterns (TODO, catch, @Deprecated, etc.)
        3. Use `read_file` to examine specific files for issues
        4. Report ALL findings via the `report_discovery` tool in a single call

        ## Rules
        - Be thorough but practical — focus on actionable improvements
        - Each discovery must reference specific files
        - Estimate effort realistically
        - Do NOT use built-in filesystem tools. Use the custom tools provided.
        - Report findings via `report_discovery` tool ONCE with all discoveries
        </rules>
        """;

    private final CopilotClient client;
    private final String model;
    private final File workingDir;
    private final List<String> categories;

    public CodebaseAnalysisSession(CopilotClient client, String model,
                                    File workingDir, List<String> categories) {
        this.client = client;
        this.model = model;
        this.workingDir = workingDir;
        this.categories = categories;
    }

    public List<ReportDiscoveryTool.Discovery> run() throws Exception {
        var discoveryTool = new ReportDiscoveryTool();
        var readFileTool = new ReadFileTool(workingDir);
        var listDirTool = new ListDirectoryTool(workingDir);
        var searchCodeTool = new SearchCodeTool(workingDir);
        var executeCommandTool = new ExecuteCommandTool(workingDir, false);

        var session = client.createSession(
            new SessionConfig()
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setModel(model)
                .setWorkingDirectory(workingDir.getAbsolutePath())
                .setTools(List.of(
                    discoveryTool.build(),
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

        session.on(AssistantMessageEvent.class, msg -> {});
        session.on(SessionErrorEvent.class, err -> {
            System.err.println("[CodebaseAnalysis] Error: " + err.getData().message());
            done.complete(null);
        });
        session.on(SessionIdleEvent.class, idle -> done.complete(null));

        String prompt = "Analyze this codebase for improvement opportunities.";
        if (categories != null && !categories.isEmpty()) {
            prompt += " Focus only on these categories: " + String.join(", ", categories);
        }
        prompt += " Report all findings via the report_discovery tool.";

        session.send(new MessageOptions().setPrompt(prompt)).get();
        done.get();
        session.close();

        return discoveryTool.getDiscoveries();
    }
}
