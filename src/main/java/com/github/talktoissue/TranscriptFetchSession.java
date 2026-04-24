package com.github.talktoissue;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.generated.AssistantMessageEvent;
import com.github.copilot.sdk.generated.SessionErrorEvent;
import com.github.copilot.sdk.generated.SessionIdleEvent;
import com.github.copilot.sdk.generated.ToolExecutionCompleteEvent;
import com.github.copilot.sdk.generated.ToolExecutionStartEvent;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.PermissionHandler;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SystemMessageConfig;
import com.github.copilot.sdk.json.ToolDefinition;
import com.github.copilot.sdk.SystemMessageMode;
import com.github.talktoissue.tools.ReportTranscriptTool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class TranscriptFetchSession {

    private static final String SYSTEM_PROMPT = """
        <rules>
        You are a meeting transcript retriever.
        Your job is to:
        1. Use the `ask_work_iq` tool ONCE to retrieve the meeting transcript for the meeting specified by the user's query.
        2. Call the `report_transcript` tool ONCE with the retrieved transcript.

        Guidelines:
        - Call `ask_work_iq` ONLY ONCE. Do NOT make multiple calls.
        - If the response contains the transcript or a summary, use that as-is.
        - Do NOT try to fetch Teams chat, emails, SharePoint documents, or any other data.
        - Call `report_transcript` immediately after receiving the `ask_work_iq` response.
        - Use the same language as the user's query for the output.
        </rules>
        """;

    private final CopilotClient client;
    private final String model;
    private final String tenantId;

    public TranscriptFetchSession(CopilotClient client, String model, String tenantId) {
        this.client = client;
        this.model = model;
        this.tenantId = tenantId;
    }

    public String run(String query) throws Exception {
        // Accept Work IQ EULA before starting session
        acceptEula();

        var reportTool = new ReportTranscriptTool();
        var askWorkIqTool = buildAskWorkIqTool();

        var session = client.createSession(
            new SessionConfig()
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setModel(model)
                .setTools(List.of(reportTool.build(), askWorkIqTool))
                .setSystemMessage(new SystemMessageConfig()
                    .setMode(SystemMessageMode.APPEND)
                    .setContent(SYSTEM_PROMPT))
        ).get();

        var done = new CompletableFuture<Void>();

        session.on(ToolExecutionStartEvent.class, event ->
            System.out.println("[Tool Start] " + event.getData()));
        session.on(ToolExecutionCompleteEvent.class, event ->
            System.out.println("[Tool Complete] " + event.getData()));
        session.on(AssistantMessageEvent.class, msg ->
            System.out.println("[Copilot] " + msg.getData().content()));
        session.on(SessionErrorEvent.class, err ->
            done.completeExceptionally(new RuntimeException(err.getData().message())));
        session.on(SessionIdleEvent.class, idle ->
            done.complete(null));

        String prompt = """
            以下の会議のトランスクリプト（録画の文字起こし）を取得してください。

            クエリ: %s

            手順:
            1. ask_work_iq を1回だけ呼んでトランスクリプトを取得してください。
            2. 取得結果をそのまま report_transcript で報告してください。
            """.formatted(query);

        session.send(new MessageOptions().setPrompt(prompt)).get();
        done.get();
        session.close();

        var compiled = reportTool.getCompiledTranscript();
        if (compiled == null) {
            throw new RuntimeException("Failed to retrieve transcript from Work IQ. "
                + "The LLM did not call report_transcript. Try a more specific query.");
        }

        return compiled.transcript();
    }

    /**
     * Build a custom ask_work_iq tool that calls the Work IQ CLI as a subprocess.
     * This bypasses MCP entirely, avoiding the SDK's MCP permission handling issue.
     */
    private ToolDefinition buildAskWorkIqTool() {
        return ToolDefinition.createSkipPermission(
            "ask_work_iq",
            "Query Microsoft 365 data (Teams meetings, chats, emails, SharePoint documents, calendar) "
                + "using natural language. Returns relevant information from the user's Microsoft 365 account. "
                + "The query should be in natural language describing what you want to find.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "query", Map.of("type", "string", "description",
                        "Natural language query to search Microsoft 365 data. "
                            + "Examples: 'meeting about project X last week', "
                            + "'Teams messages about deployment from yesterday', "
                            + "'emails about budget review'")
                ),
                "required", List.of("query")
            ),
            invocation -> CompletableFuture.supplyAsync(() -> {
                String q = (String) invocation.getArguments().get("query");
                System.out.println("[Work IQ] Querying: " + q);
                try {
                    return runWorkIqQuery(q);
                } catch (Exception e) {
                    return "Error querying Work IQ: " + e.getMessage();
                }
            })
        );
    }

    private static final long WORKIQ_TIMEOUT_SECONDS = 180;

    private String runWorkIqQuery(String query) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(
            "npx", "-y", "@microsoft/workiq@latest", "ask", "-q", query
        ));
        if (tenantId != null && !tenantId.isBlank()) {
            command.add("--tenant-id");
            command.add(tenantId);
        }

        var pb = new ProcessBuilder(command)
            .redirectErrorStream(true);
        // Inherit stdin so OAuth device-code / interactive prompts can work
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        var process = pb.start();

        // Read output char by char to handle spinners, prompts, and partial lines
        var output = new StringBuilder();
        var is = process.getInputStream();
        var buf = new byte[4096];
        int len;
        while ((len = is.read(buf)) != -1) {
            String chunk = new String(buf, 0, len);
            System.out.print(chunk);
            System.out.flush();
            output.append(chunk);
        }

        boolean finished = process.waitFor(WORKIQ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return "Work IQ query timed out after " + WORKIQ_TIMEOUT_SECONDS + " seconds.";
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            System.err.println("[Work IQ] Exit code: " + exitCode);
            return "Work IQ query failed (exit code " + exitCode + "): " + output;
        }

        System.out.println("\n[Work IQ] Response length: " + output.length() + " chars");
        return output.toString();
    }

    private void acceptEula() throws IOException, InterruptedException {
        System.out.println("Accepting Work IQ EULA...");
        List<String> command = new ArrayList<>(List.of("npx", "-y", "@microsoft/workiq@latest", "accept-eula"));
        if (tenantId != null && !tenantId.isBlank()) {
            command.add("--tenant-id");
            command.add(tenantId);
        }

        var process = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            System.err.println("Warning: Work IQ EULA acceptance returned exit code " + exitCode);
            System.err.println(output);
        } else {
            System.out.println("Work IQ EULA accepted.");
        }
    }
}
