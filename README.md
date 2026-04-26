# talk-to-issue

**あらゆるコンテキスト**（会議録、インシデントレポート、設計書、GitHub Issues など）を解析し、GitHub Issue の作成・実装・PR 作成を自動化するエージェント。[GitHub Copilot SDK for Java](https://github.github.io/copilot-sdk-java/) を使用。

（中略）

## License

MIT

## トラブルシューティング

| エラー例 | 原因 | 対処法 |
|----------|------|--------|
| ```
Error: GITHUB_TOKEN is not set
or
401 Unauthorized: Bad credentials
``` | 環境変数 `GITHUB_TOKEN` が未設定、または無効 | 有効なGitHub Personal Access Tokenを取得し、`export GITHUB_TOKEN=...` で設定してください。スコープは`repo`が必要です。|
| ```
401 Invalid signature
``` | Webhook Secret（`--webhook-secret`）が未設定、またはGitHub側と一致していない | `--webhook-secret` オプションでサーバー起動時に正しいシークレットを指定し、GitHub Webhook設定でも同じ値を入力してください。|
| ```
java.net.BindException: Address already in use
``` | 指定したポート（例: 8080）が他プロセスで使用中 | `--port` オプションで空いているポート番号を指定するか、既存プロセスを停止してください。|
| ```
Error: Could not find or load main class ...
Caused by: java.lang.ClassNotFoundException
``` | 依存パッケージが不足、またはビルド未実施 | `mvn clean package -DskipTests` を再実行し、`target/`配下にJARが生成されているか確認してください。|

> その他のエラーが発生した場合は、`-h`/`--help` オプションや、`mvn dependency:tree` で依存関係を確認してください。
