---
name: github-copilot-sdk-java
description: "Expert guidance for building Java applications with the GitHub Copilot SDK (copilot-sdk-java). Use when: writing Copilot SDK Java code, creating CopilotClient/CopilotSession, defining custom tools with ToolDefinition, configuring SessionConfig, handling events (AssistantMessageEvent, SessionIdleEvent), streaming responses, BYOK setup, MCP server integration, session hooks, permission handling, reviewing or debugging Copilot SDK Java code, GitHub API integration via Copilot tools."
---

# GitHub Copilot SDK for Java

Expert skill for building, reviewing, and debugging Java applications powered by the GitHub Copilot SDK (`com.github:copilot-sdk-java`).

## When to Use

- Creating a new Java project that integrates with GitHub Copilot
- Writing code that uses `CopilotClient`, `CopilotSession`, `ToolDefinition`, or `SessionConfig`
- Adding custom tools, MCP servers, or custom agents to a Copilot-powered Java app
- Debugging event handling, streaming, or connection issues with the SDK
- Reviewing code that uses the Copilot SDK for correctness and best practices
- Setting up OAuth, BYOK, or backend deployment for Copilot SDK apps

## SDK Overview

- **Version**: `0.3.0-java-preview.1` (preview)
- **Java requirement**: Java 17+
- **Maven coordinates**: `com.github:copilot-sdk-java:0.3.0-java-preview.1`
- **Architecture**: The SDK manages a connection to the Copilot CLI process. Sessions are independent conversations that can run concurrently.
- **Async model**: All SDK methods return `CompletableFuture`. Use `.get()` for blocking calls or compose with `.thenApply()`, `.exceptionally()`, etc.
- **Official docs**: https://github.github.io/copilot-sdk-java/latest/documentation.html
- **Javadoc**: https://github.github.io/copilot-sdk-java/latest/apidocs/index.html

## Review Checklist

When reviewing or writing Copilot SDK Java code, verify the following:

### 1. Client Lifecycle
- [ ] `CopilotClient` is used with try-with-resources (`try (var client = new CopilotClient())`)
- [ ] `client.start().get()` is called before creating sessions
- [ ] Sessions are closed when no longer needed (`session.close()`)
- [ ] For permanent deletion, use `client.deleteSession(sessionId).get()`

### 2. SessionConfig
- [ ] `PermissionHandler` is set (use `PermissionHandler.APPROVE_ALL` for dev, custom handler for production)
- [ ] Model is specified via `.setModel("gpt-4.1")` or similar
- [ ] Streaming is explicitly set if needed: `.setStreaming(true)`
- [ ] Working directory is set if file operations are needed: `.setWorkingDirectory("/path")`

### 3. Event Handling
- [ ] `SessionIdleEvent` is handled to detect when processing is complete
- [ ] `SessionErrorEvent` is handled for error cases
- [ ] For streaming: `AssistantMessageDeltaEvent` handlers are registered
- [ ] For complete messages: `AssistantMessageEvent` handlers are registered
- [ ] `CompletableFuture<Void>` pattern is used to wait for async completion
- [ ] Event error policy is set appropriately (default: `PROPAGATE_AND_LOG_ERRORS`)

### 4. Custom Tools
- [ ] `ToolDefinition.create()` is used with: name, description, JSON schema, handler
- [ ] Tool handler returns `CompletableFuture<Object>` (can be String, Map, etc.)
- [ ] Schema uses proper JSON Schema format with `type`, `properties`, `required`
- [ ] `invocation.getArgumentsAs(RecordClass.class)` is used for type-safe argument parsing
- [ ] Read-only tools use `ToolDefinition.createSkipPermission()` to skip permission prompts
- [ ] Built-in tool overrides use `ToolDefinition.createOverride()`

### 5. Error Handling
- [ ] `ExecutionException` is caught around `.get()` calls
- [ ] `TimeoutException` is handled for `sendAndWait()` with custom timeout
- [ ] `session.abort().get()` is called on timeout or cancellation
- [ ] Reactive error handling uses `.exceptionally()` or `.handle()`

### 6. Security
- [ ] GitHub tokens are never logged or exposed
- [ ] Tokens come from environment variables in production
- [ ] Custom permission handlers validate tool invocations in production
- [ ] User input is validated before passing to sessions

## Key Patterns

### Basic Request-Response

```java
try (var client = new CopilotClient()) {
    client.start().get();
    var session = client.createSession(
        new SessionConfig()
            .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
            .setModel("gpt-4.1")
    ).get();

    var response = session.sendAndWait("Your prompt here").get();
    System.out.println(response.getData().content());
    session.close();
}
```

### Streaming with Event Handlers

```java
var session = client.createSession(
    new SessionConfig()
        .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
        .setModel("gpt-4.1")
        .setStreaming(true)
).get();

var done = new CompletableFuture<Void>();

session.on(AssistantMessageDeltaEvent.class, delta ->
    System.out.print(delta.getData().deltaContent()));
session.on(SessionErrorEvent.class, err ->
    System.err.println("Error: " + err.getData().message()));
session.on(SessionIdleEvent.class, idle ->
    done.complete(null));

session.send("Your prompt here").get();
done.get();
```

### Custom Tool Definition

```java
record IssueArgs(String id) {}

var lookupTool = ToolDefinition.create(
    "lookup_issue",
    "Fetch issue details from the tracker",
    Map.of(
        "type", "object",
        "properties", Map.of(
            "id", Map.of("type", "string", "description", "Issue identifier")
        ),
        "required", List.of("id")
    ),
    invocation -> {
        IssueArgs args = invocation.getArgumentsAs(IssueArgs.class);
        return CompletableFuture.completedFuture(fetchIssue(args.id()));
    }
);

var session = client.createSession(
    new SessionConfig()
        .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
        .setTools(List.of(lookupTool))
).get();
```

### System Message Customization

```java
// APPEND mode: add rules while keeping defaults
new SessionConfig()
    .setSystemMessage(new SystemMessageConfig()
        .setMode(SystemMessageMode.APPEND)
        .setContent("<rules>\n- Always check for security vulnerabilities\n</rules>"))

// REPLACE mode: full control (removes default guardrails)
new SessionConfig()
    .setSystemMessage(new SystemMessageConfig()
        .setMode(SystemMessageMode.REPLACE)
        .setContent("You are a helpful coding assistant."))

// CUSTOMIZE mode: override specific sections
new SessionConfig()
    .setSystemMessage(new SystemMessageConfig()
        .setMode(SystemMessageMode.CUSTOMIZE)
        .setSections(Map.of(
            SystemPromptSections.TONE,
            new SectionOverride()
                .setAction(SectionOverrideAction.REPLACE)
                .setContent("Be concise and formal."))))
```

### MCP Server Integration

```java
Map<String, Object> server = Map.of(
    "type", "local",
    "command", "npx",
    "args", List.of("-y", "@modelcontextprotocol/server-filesystem", "/tmp"),
    "tools", List.of("*")
);

var session = client.createSession(
    new SessionConfig()
        .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
        .setMcpServers(Map.of("filesystem", server))
).get();
```

### Session Hooks

```java
var hooks = new SessionHooks()
    .setOnPreToolUse((input, invocation) -> {
        System.out.println("Tool: " + input.getToolName());
        return CompletableFuture.completedFuture(PreToolUseHookOutput.allow());
    })
    .setOnPostToolUse((input, invocation) -> {
        System.out.println("Result: " + input.getToolResult());
        return CompletableFuture.completedFuture(null);
    });

var session = client.createSession(
    new SessionConfig()
        .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
        .setHooks(hooks)
).get();
```

### Custom Agents

```java
var reviewer = new CustomAgentConfig()
    .setName("code-reviewer")
    .setDisplayName("Code Reviewer")
    .setDescription("Reviews code for best practices and security")
    .setPrompt("You are a code review expert.")
    .setTools(List.of("read_file", "search_code"));

var session = client.createSession(
    new SessionConfig()
        .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
        .setCustomAgents(List.of(reviewer))
).get();

// User can invoke with: @code-reviewer Review src/Main.java
session.send("@code-reviewer Review src/Main.java").get();
```

### Tool Filtering

```java
// Allowlist: only these built-in tools
new SessionConfig()
    .setAvailableTools(List.of("read_file", "search_code", "list_dir"))

// Blocklist: all except these
new SessionConfig()
    .setExcludedTools(List.of("execute_command", "write_file"))
```

### BYOK (Bring Your Own Key)

```java
// OpenAI
new SessionConfig()
    .setProvider(new ProviderConfig()
        .setType("openai")
        .setBaseUrl("https://api.openai.com/v1")
        .setApiKey("sk-..."))

// Azure OpenAI
new SessionConfig()
    .setProvider(new ProviderConfig()
        .setType("azure")
        .setBaseUrl("https://your-resource.openai.azure.com/")
        .setApiKey("your-key"))
```

### Connection State Check

```java
ConnectionState state = client.getState();
// DISCONNECTED → CONNECTING → CONNECTED or ERROR

var auth = client.getAuthStatus().get();
if (auth.isAuthenticated()) {
    System.out.println("Logged in as: " + auth.getLogin());
}
```

## GitHub API Integration Patterns

Copilot SDK のカスタムツールを使って GitHub API（Issue, PR など）を操作するパターン。

### Issue 取得ツール

```java
record GetIssueArgs(String owner, String repo, int number) {}

var getIssueTool = ToolDefinition.create(
    "get_issue",
    "Fetch a GitHub issue by number, including title, body, labels, and assignees",
    Map.of(
        "type", "object",
        "properties", Map.of(
            "owner", Map.of("type", "string", "description", "Repository owner"),
            "repo", Map.of("type", "string", "description", "Repository name"),
            "number", Map.of("type", "integer", "description", "Issue number")
        ),
        "required", List.of("owner", "repo", "number")
    ),
    invocation -> {
        var args = invocation.getArgumentsAs(GetIssueArgs.class);
        // Use GitHub API client (e.g., org.kohsuke:github-api or OkHttp)
        var issue = gitHub.getRepository(args.owner() + "/" + args.repo())
                         .getIssue(args.number());
        return CompletableFuture.completedFuture(Map.of(
            "title", issue.getTitle(),
            "body", issue.getBody(),
            "state", issue.getState().toString(),
            "labels", issue.getLabels().stream().map(GHLabel::getName).toList(),
            "assignees", issue.getAssignees().stream().map(GHUser::getLogin).toList()
        ));
    }
);
```

### Issue 検索ツール

```java
var searchIssuesTool = ToolDefinition.createSkipPermission(
    "search_issues",
    "Search GitHub issues by query string (read-only)",
    Map.of(
        "type", "object",
        "properties", Map.of(
            "owner", Map.of("type", "string"),
            "repo", Map.of("type", "string"),
            "query", Map.of("type", "string", "description", "Search keywords"),
            "state", Map.of("type", "string", "enum", List.of("open", "closed", "all"))
        ),
        "required", List.of("owner", "repo", "query")
    ),
    invocation -> {
        var args = invocation.getArguments();
        String q = args.get("query") + " repo:" + args.get("owner") + "/" + args.get("repo");
        String state = (String) args.getOrDefault("state", "open");
        if (!"all".equals(state)) {
            q += " state:" + state;
        }
        var results = gitHub.searchIssues().q(q).list().toList();
        return CompletableFuture.completedFuture(
            results.stream().map(issue -> Map.of(
                "number", issue.getNumber(),
                "title", issue.getTitle(),
                "state", issue.getState().toString()
            )).toList()
        );
    }
);
```

### Issue コメント投稿ツール

```java
var commentIssueTool = ToolDefinition.create(
    "comment_on_issue",
    "Post a comment on a GitHub issue",
    Map.of(
        "type", "object",
        "properties", Map.of(
            "owner", Map.of("type", "string"),
            "repo", Map.of("type", "string"),
            "number", Map.of("type", "integer"),
            "body", Map.of("type", "string", "description", "Comment body in Markdown")
        ),
        "required", List.of("owner", "repo", "number", "body")
    ),
    invocation -> {
        // This is a write operation — PermissionHandler will be invoked
        var args = invocation.getArguments();
        var issue = gitHub.getRepository(args.get("owner") + "/" + args.get("repo"))
                         .getIssue((int) args.get("number"));
        issue.comment((String) args.get("body"));
        return CompletableFuture.completedFuture(
            Map.of("status", "commented", "issue", args.get("number"))
        );
    }
);
```

### PR 情報取得ツール

```java
var getPRTool = ToolDefinition.createSkipPermission(
    "get_pull_request",
    "Fetch pull request details including diff stats, review status, and checks",
    Map.of(
        "type", "object",
        "properties", Map.of(
            "owner", Map.of("type", "string"),
            "repo", Map.of("type", "string"),
            "number", Map.of("type", "integer")
        ),
        "required", List.of("owner", "repo", "number")
    ),
    invocation -> {
        var args = invocation.getArguments();
        var pr = gitHub.getRepository(args.get("owner") + "/" + args.get("repo"))
                       .getPullRequest((int) args.get("number"));
        return CompletableFuture.completedFuture(Map.of(
            "title", pr.getTitle(),
            "body", pr.getBody() != null ? pr.getBody() : "",
            "state", pr.getState().toString(),
            "mergeable", pr.getMergeable(),
            "additions", pr.getAdditions(),
            "deletions", pr.getDeletions(),
            "changedFiles", pr.getChangedFiles(),
            "head", pr.getHead().getRef(),
            "base", pr.getBase().getRef()
        ));
    }
);
```

### 複数ツールをセッションに登録

```java
var session = client.createSession(
    new SessionConfig()
        .setOnPermissionRequest((request, invocation) -> {
            // 書き込み操作のみ承認確認
            String tool = request.getToolName();
            if (tool.equals("comment_on_issue") || tool.equals("create_issue")) {
                System.out.println("Allow " + tool + "? (y/n)");
                // ... ユーザー確認ロジック
            }
            var result = new PermissionRequestResult();
            result.setKind(PermissionRequestResultKind.APPROVED);
            return CompletableFuture.completedFuture(result);
        })
        .setModel("gpt-4.1")
        .setTools(List.of(getIssueTool, searchIssuesTool, commentIssueTool, getPRTool))
        .setSystemMessage(new SystemMessageConfig()
            .setMode(SystemMessageMode.APPEND)
            .setContent("""
                <rules>
                - You have access to GitHub Issue and PR tools.
                - Always confirm with the user before posting comments or creating issues.
                - Summarize issue content concisely.
                </rules>
            """))
).get();
```

## Event Types Quick Reference

| Category | Key Events |
|---|---|
| Session | `SessionStartEvent`, `SessionIdleEvent`, `SessionErrorEvent`, `SessionShutdownEvent` |
| Assistant | `AssistantMessageEvent`, `AssistantMessageDeltaEvent`, `AssistantReasoningEvent` |
| Tool | `ToolExecutionStartEvent`, `ToolExecutionCompleteEvent` |
| Permission | `PermissionRequestedEvent`, `PermissionCompletedEvent` |
| Subagent | `SubagentStartedEvent`, `SubagentCompletedEvent`, `SubagentFailedEvent` |

All events extend `SessionEvent`. Use `session.on(EventClass.class, handler)` for type-safe handling or `session.on(event -> { switch (event) { ... } })` for catch-all.

## Common Mistakes

| Mistake | Fix |
|---|---|
| Not calling `client.start().get()` | Always start the client before creating sessions |
| Missing `SessionIdleEvent` handler | Always handle idle to know when processing is complete |
| Not using try-with-resources for `CopilotClient` | Use `try (var client = new CopilotClient())` to ensure cleanup |
| Blocking the event handler thread | Return `CompletableFuture` from tool handlers; do not block |
| Hardcoding tokens | Use environment variables: `System.getenv("GITHUB_TOKEN")` |
| Not setting `PermissionHandler` | Session creation requires `onPermissionRequest` to be set |
| Using `APPROVE_ALL` in production | Implement a custom `PermissionHandler` that validates each request |

## Deployment Patterns

| Scenario | Configuration |
|---|---|
| Local development | Default `new CopilotClient()` (uses local CLI) |
| Multi-user OAuth app | `new CopilotClientOptions().setGitHubToken(userToken).setUseLoggedInUser(false)` |
| Backend service | `new CopilotClientOptions().setCliUrl("cli-server:4321")` + `copilot server --port 4321` |
| Bundled CLI | `new CopilotClientOptions().setCliPath("./bin/copilot")` |

## Reference Links

- [Getting Started](https://github.github.io/copilot-sdk-java/latest/getting-started.html)
- [Documentation](https://github.github.io/copilot-sdk-java/latest/documentation.html)
- [Advanced Usage](https://github.github.io/copilot-sdk-java/latest/advanced.html)
- [Setup & Deployment](https://github.github.io/copilot-sdk-java/latest/setup.html)
- [Session Hooks](https://github.github.io/copilot-sdk-java/latest/hooks.html)
- [MCP Servers](https://github.github.io/copilot-sdk-java/latest/mcp.html)
- [API Javadoc](https://github.github.io/copilot-sdk-java/latest/apidocs/index.html)
- [Cookbook](https://github.github.io/copilot-sdk-java/latest/cookbook/README.html)