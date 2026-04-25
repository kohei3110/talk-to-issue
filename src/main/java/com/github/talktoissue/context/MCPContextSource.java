package com.github.talktoissue.context;

import com.github.copilot.sdk.CopilotClient;
import com.github.copilot.sdk.CopilotSession;
import com.github.copilot.sdk.generated.AssistantMessageEvent;
import com.github.copilot.sdk.generated.SessionErrorEvent;
import com.github.copilot.sdk.generated.SessionIdleEvent;
import com.github.copilot.sdk.json.McpStdioServerConfig;
import com.github.copilot.sdk.json.MessageOptions;
import com.github.copilot.sdk.json.PermissionHandler;
import com.github.copilot.sdk.json.SessionConfig;
import com.github.copilot.sdk.json.SystemMessageConfig;
import com.github.copilot.sdk.SystemMessageMode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches context via an arbitrary MCP server.
 * The Copilot SDK session is configured with the MCP server, and the LLM is asked
 * to invoke the specified tool and return the result.
 */
public class MCPContextSource implements ContextSource {

    private final CopilotClient client;
    private final String model;
    private final String serverName;
    private final String command;
    private final List<String> args;
    private final String query;

    public MCPContextSource(CopilotClient client, String model,
                            String serverName, String command, List<String> args,
                            String query) {
        this.client = client;
        this.model = model;
        this.serverName = serverName;
        this.command = command;
        this.args = args;
        this.query = query;
    }

    @Override
    public String getType() {
        return "mcp";
    }

    @Override
    public String describe() {
        return "MCP server '" + serverName + "': " + query;
    }

    @Override
    public String fetch() throws Exception {
        var server = new McpStdioServerConfig()
            .setCommand(command)
            .setArgs(args)
            .setTools(List.of("*"));

        CopilotSession session = client.createSession(
            new SessionConfig()
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setModel(model)
                .setMcpServers(Map.of(serverName, server))
                .setSystemMessage(new SystemMessageConfig()
                    .setMode(SystemMessageMode.APPEND)
                    .setContent("<rules>You are a context retrieval assistant. "
                        + "Use the available MCP tools to answer the user's query. "
                        + "Return the raw result without summarization.</rules>"))
        ).get();

        var result = new StringBuilder();
        var done = new CompletableFuture<Void>();

        session.on(AssistantMessageEvent.class, msg ->
            result.append(msg.getData().content()));
        session.on(SessionErrorEvent.class, err ->
            done.completeExceptionally(new RuntimeException(err.getData().message())));
        session.on(SessionIdleEvent.class, idle ->
            done.complete(null));

        session.send(new MessageOptions().setPrompt(query)).get();
        done.get();
        session.close();

        return result.toString();
    }
}
