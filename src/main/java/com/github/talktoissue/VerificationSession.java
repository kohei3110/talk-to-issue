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
import com.github.talktoissue.tools.ReportVerificationTool;
import com.github.talktoissue.tools.SearchCodeTool;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Runs build and test verification after implementation.
 * Returns a structured result indicating whether the build/tests passed.
 */
public class VerificationSession {

    private static final String SYSTEM_PROMPT = """
        <rules>
        You are a build and test verification agent. Your job is to verify that code changes
        compile correctly and pass tests.

        ## Procedure
        1. Explore the project structure using `list_dir` to understand the build system.
           - Look for pom.xml (Maven), build.gradle (Gradle), package.json (Node.js), Makefile, etc.
        2. Run the appropriate build command using `execute_command`:
           - Maven: `mvn compile -q` or `mvn package -DskipTests -q`
           - Gradle: `./gradlew compileJava`
           - Node.js: `npm run build`
           - Go: `go build ./...`
        3. Run the appropriate test command using `execute_command`:
           - Maven: `mvn test -q`
           - Gradle: `./gradlew test`
           - Node.js: `npm test`
           - Go: `go test ./...`
           - If no test framework is detected, report testsSuccess=true with a note.
        4. If build or tests fail, use `read_file` and `search_code` to investigate the errors.
        5. Report results using `report_verification`.

        ## Important
        - Do NOT use built-in filesystem tools (glob, view, grep). Use the custom tools instead.
        - Do NOT attempt to fix the code. Only verify and report.
        - If build fails, still try to identify which specific errors occurred.
        - Include actionable error messages in the report, not the full build log.
        - If tests fail, list each failing test with its error message.
        </rules>
        """;

    private final CopilotClient client;
    private final String model;
    private final File workingDir;
    private final boolean dryRun;

    public VerificationSession(CopilotClient client, String model, File workingDir, boolean dryRun) {
        this.client = client;
        this.model = model;
        this.workingDir = workingDir;
        this.dryRun = dryRun;
    }

    public ReportVerificationTool.VerificationResult run() throws Exception {
        var readFileTool = new ReadFileTool(workingDir);
        var listDirTool = new ListDirectoryTool(workingDir);
        var searchCodeTool = new SearchCodeTool(workingDir);
        var executeCommandTool = new ExecuteCommandTool(workingDir, dryRun);
        var reportTool = new ReportVerificationTool();

        var session = client.createSession(
            new SessionConfig()
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setModel(model)
                .setWorkingDirectory(workingDir.getAbsolutePath())
                .setTools(List.of(
                    readFileTool.build(),
                    listDirTool.build(),
                    searchCodeTool.build(),
                    executeCommandTool.build(),
                    reportTool.build()
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
            実装後のビルドとテストの検証を実行してください。

            手順:
            1. list_dir でプロジェクト構造を確認し、ビルドシステムを特定
            2. execute_command でビルドを実行
            3. execute_command でテストを実行
            4. 結果を分析し、report_verification で報告
            """;

        session.send(new MessageOptions().setPrompt(prompt)).get();
        done.get();
        session.close();

        var result = reportTool.getVerificationResult();
        if (result == null) {
            throw new RuntimeException("Verification result was not reported. The session completed without calling report_verification.");
        }
        return result;
    }
}
