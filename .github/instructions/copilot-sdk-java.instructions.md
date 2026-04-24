---
applyTo: "**/*.java"
description: "Use when writing or modifying Java code that uses the GitHub Copilot SDK (copilot-sdk-java). Covers CopilotClient, CopilotSession, ToolDefinition, SessionConfig, event handling, streaming, and permission handling patterns."
---

# GitHub Copilot SDK for Java — コーディング規約

## Client Lifecycle

- `CopilotClient` は必ず try-with-resources で使う
- `client.start().get()` をセッション作成前に呼ぶ
- セッション終了時は `session.close()` を呼ぶ

```java
try (var client = new CopilotClient()) {
    client.start().get();
    var session = client.createSession(config).get();
    // ...
    session.close();
}
```

## SessionConfig 必須設定

- `setOnPermissionRequest()` は必須。開発時は `PermissionHandler.APPROVE_ALL`、本番は独自ハンドラ
- `setModel()` でモデルを明示指定する（例: `"gpt-4.1"`, `"claude-sonnet-4"`）

## イベントハンドリング

- `SessionIdleEvent` を必ずハンドルし、処理完了を検知する
- `SessionErrorEvent` を必ずハンドルし、エラーを捕捉する
- ストリーミング時は `AssistantMessageDeltaEvent` を使う
- 完了待ちには `CompletableFuture<Void>` パターンを使う

```java
var done = new CompletableFuture<Void>();
session.on(SessionIdleEvent.class, idle -> done.complete(null));
session.on(SessionErrorEvent.class, err ->
    done.completeExceptionally(new RuntimeException(err.getData().message())));
session.send(new MessageOptions().setPrompt("...")).get();
done.get();
```

## カスタムツール

- `ToolDefinition.create()` の JSON Schema は `type`, `properties`, `required` を含める
- ハンドラは `CompletableFuture` を返す。ブロッキング処理は `CompletableFuture.supplyAsync()` で包む
- 読み取り専用ツールは `ToolDefinition.createSkipPermission()` を使う
- 組み込みツールの上書きは `ToolDefinition.createOverride()` を使う
- 引数の型安全な取得には `invocation.getArgumentsAs(RecordClass.class)` を使う

## エラーハンドリング

- `.get()` 呼び出しには `ExecutionException` の catch を付ける
- タイムアウト時は `session.abort().get()` でキャンセルする
- 複数ハンドラで fan-out する場合は `EventErrorPolicy.SUPPRESS_AND_LOG_ERRORS` を設定する

## セキュリティ

- GitHub トークンをログに出力しない
- 本番ではトークンは環境変数から取得する: `System.getenv("GITHUB_TOKEN")`
- 本番では `PermissionHandler.APPROVE_ALL` を使わず、ツール名を検証するカスタムハンドラを実装する
