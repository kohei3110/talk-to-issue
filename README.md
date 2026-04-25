# talk-to-issue

**あらゆるコンテキスト**（会議録、インシデントレポート、設計書、GitHub Issues など）を解析し、GitHub Issue の作成・実装・PR 作成を自動化するエージェント。[GitHub Copilot SDK for Java](https://github.github.io/copilot-sdk-java/) を使用。

ローカルマシン上で自律的に稼働するサーバーモード（`serve`）を搭載し、GitHub Webhook をトリガーにパイプラインを継続的に実行可能。

## Features

| Feature | Command | Description |
|---------|---------|-------------|
| **Issue Compiler** | `compile` | 任意のコンテキストを coding-agent-ready な Issue に変換。概要・受け入れ基準・技術コンテキスト・スコープ・テスト要件を含む構造化テンプレート。 |
| **Issue Quality Scorer** | `score` | Issue が AI coding agent に渡せる品質か採点（0〜100点）。6軸評価。 |
| **Intent Drift Detector** | `drift` | PR が会議決定・Issue 要件からズレていないか検出。pass/warn/fail で判定。 |
| **Full Pipeline** | `pipeline` | compile → score → implement → drift detection のフルパイプライン。 |
| **Autonomous Server** | `serve` | Webhook 受信 + 定期ポーリングで自律稼働するエージェントサーバー。 |

## Architecture

```
                     ┌─────────────────────────────────────┐
                     │         Context Sources              │
                     │  ┌──────┐ ┌──────┐ ┌──────┐ ┌─────┐│
                     │  │ File │ │WorkIQ│ │GitHub│ │ MCP ││
                     │  └──┬───┘ └──┬───┘ └──┬───┘ └──┬──┘│
                     └─────┼────────┼────────┼────────┼───┘
                           └────────┼────────┘        │
                                    ▼                 │
                          ┌─────────────────┐         │
                          │ContextAggregator│◀────────┘
                          └────────┬────────┘
                                   ▼
  ┌─────────┐     ┌─────────┐     ┌──────────────┐     ┌───────────────┐
  │ compile  │────▶│  score  │────▶│  implement   │────▶│    drift      │
  │ Context→ │     │ 品質採点 │     │ Issue→PR     │     │ ズレ検出      │
  │ Issue作成│     │ (0-100) │     │              │     │ pass/warn/fail│
  └─────────┘     └─────────┘     └──────────────┘     └───────────────┘
        ▲                                                       │
        │              ┌────────────────────────┐               │
        └──────────────│  serve (AgentServer)   │◀──────────────┘
                       │  Webhook + Scheduler   │
                       └────────────────────────┘
```

## Prerequisites

- Java 17+
- Maven 3.8+
- A GitHub Personal Access Token (`repo` scope) — set as `GITHUB_TOKEN`
- The [Copilot CLI](https://github.github.io/copilot-sdk-java/latest/getting-started.html) installed and authenticated (`gh auth login`)
- A local clone of the target repository

## Build

```bash
mvn clean package -DskipTests
```

This produces a fat JAR at `target/talk-to-issue-1.0-SNAPSHOT.jar`.

## Usage

### Global Options

All subcommands share the following options, specified **before** the subcommand name:

| Flag | Required | Description |
|------|----------|-------------|
| `-r`, `--repo` | Yes | Target GitHub repository (`owner/repo`) |
| `-w`, `--working-dir` | No | Path to the local clone of the target repo |
| `-m`, `--model` | No | LLM model (default: `gpt-4.1`) |
| `--dry-run` | No | Simulate without creating issues/PRs |

### Context Sources

コンテキストソースは以下の方法で指定できます。すべてのソースの結果は集約されて LLM に渡されます。

#### 1. ファイル指定（従来方式）

```bash
compile -f meeting-notes.txt
```

#### 2. インラインコンテキスト（`--context`）

```bash
# ファイル
compile --context file:./examples/meeting-transcript.txt

# 複数ソース（カンマ区切り）
compile --context file:./examples/meeting-transcript.txt,file:./examples/incident-report.txt

# GitHub Issues
compile --context github:issues

# GitHub Pull Requests
compile --context github:prs
```

#### 3. YAML 設定ファイル（`--context-config`）

```bash
compile --context-config examples/context-config.yaml
```

設定ファイル例（[examples/context-config.yaml](examples/context-config.yaml)）：

```yaml
sources:
  - type: file
    path: ./examples/meeting-transcript.txt
  - type: file
    path: ./examples/incident-report.txt
  - type: github
    scope: issues
    filter: "label:needs-triage"
  - type: workiq
    query: "直近1週間の会議を要約して"
    tenant-id: "your-tenant-id"
  - type: mcp
    server: filesystem
    command: npx
    args: ["-y", "@modelcontextprotocol/server-filesystem", "/docs"]
    query: "Read the requirements document"
```

| Source Type | Description | Parameters |
|-------------|-------------|------------|
| `file` | ローカルファイル読み込み | `path` |
| `workiq` | Microsoft 365 会議データ取得 | `query`, `tenant-id` |
| `github` | GitHub Issues/PRs 取得 | `scope` (`issues`/`pull_requests`), `filter` |
| `mcp` | 任意の MCP サーバー経由 | `server`, `command`, `args`, `query` |

### `compile` — コンテキスト → Issue 作成

任意のコンテキスト（会議録、インシデントレポート、設計書など）を解析し、構造化 GitHub Issue を作成します。

```bash
# ファイル指定
java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  -r owner/repo -w /path/to/clone --dry-run \
  compile -f meeting-notes.txt

# YAML設定
java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  -r owner/repo -w /path/to/clone --dry-run \
  compile --context-config examples/context-config.yaml

# インライン指定
java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  -r owner/repo -w /path/to/clone --dry-run \
  compile --context file:./examples/incident-report.txt
```

| Flag | Description |
|------|-------------|
| `-f`, `--file` | コンテキストファイルのパス |
| `-q`, `--workiq-query` | Work IQ からコンテキストを取得するクエリ |
| `--tenant-id` | Microsoft Entra tenant ID (Work IQ 用) |
| `--context-config` | YAML 設定ファイルのパス |
| `--context` | インラインコンテキスト指定（カンマ区切りで複数可） |

### `score` — Issue品質スコアリング

GitHub IssueがAIコーディングエージェントに渡せる品質かを0〜100点で採点します。

```bash
java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  -r owner/repo -w /path/to/clone \
  score --issue 42
```

| Flag | Required | Description |
|------|----------|-------------|
| `--issue` | Yes | GitHub Issue number to score |
| `--min-score` | No | Minimum passing score (default: 0). Exit code 1 if below. |

**出力例:**
```
Overall Score: 85/100
  Clarity:             18/20 — Clear problem statement
  Specificity:         16/20 — Could include more concrete examples
  Acceptance Criteria: 20/20 — Well-defined, testable criteria
  Scope:               12/15 — Slightly broad
  Testability:         12/15 — Test scenarios implied but not explicit
  Context:              7/10 — Missing architecture context
```

### `drift` — Intent Drift検出

PRが会議決定やIssue要件からズレていないかを検出します。

```bash
java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  -r owner/repo -w /path/to/clone \
  drift --pr 10 --issue 42
```

| Flag | Required | Description |
|------|----------|-------------|
| `--pr` | Yes | Pull Request number to analyze |
| `--issue` | No | Linked Issue number for comparison |
| `-f`, `--file` | No | Meeting transcript for additional context |

**判定:**
- `pass` — ドリフトなし
- `warn` — 軽微なドリフトあり（レビュー推奨）
- `fail` — 重大なドリフト検出（exit code 1）

### `run` — フルワークフロー

従来の一気通貫ワークフロー（会議録 → Issue作成 → 実装 → PR作成）。

```bash
java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  -r owner/repo -w /path/to/clone --dry-run \
  run -f meeting-notes.txt
```

### `pipeline` — フルパイプライン

compile → score → implement → drift detection を順次実行します。

```bash
# ファイル指定
java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  -r owner/repo -w /path/to/clone --dry-run \
  pipeline -f meeting-notes.txt --min-score 70

# YAML設定（複数ソースを集約してパイプライン実行）
java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  -r owner/repo -w /path/to/clone --dry-run \
  pipeline --context-config examples/context-config.yaml --min-score 70
```

| Flag | Description |
|------|-------------|
| `-f`, `--file` | コンテキストファイルのパス |
| `-q`, `--workiq-query` | Work IQ からコンテキストを取得するクエリ |
| `--tenant-id` | Microsoft Entra tenant ID (Work IQ 用) |
| `--context-config` | YAML 設定ファイルのパス |
| `--context` | インラインコンテキスト指定 |
| `--min-score` | 実装に進む最低品質スコア（デフォルト: 70） |

### `serve` — 自律稼働エージェントサーバー

ローカルマシン上で常駐し、GitHub Webhook をトリガーにパイプラインを自動実行するサーバーモード。

```bash
java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  -r owner/repo -w /path/to/clone \
  serve --port 8080 --trigger-label agent-ready
```

| Flag | Default | Description |
|------|---------|-------------|
| `--port` | `8080` | HTTP サーバーポート |
| `--webhook-secret` | — | GitHub Webhook Secret（HMAC-SHA256 署名検証） |
| `--context-config` | — | 定期ポーリング用 YAML 設定 |
| `--poll-interval` | `0` | ポーリング間隔（分）。0 = 無効 |
| `--trigger-label` | `agent-ready` | 実装をトリガーする Issue ラベル |
| `--concurrency` | `2` | 最大同時パイプライン実行数 |

**Webhook イベント対応:**

| Event | Trigger | Action |
|-------|---------|--------|
| `issues.labeled` | `agent-ready` ラベル付与 | Issue を採点 → 実装 → PR 作成 |
| `issue_comment.created` | `/pipeline` コメント | Issue コンテキストでパイプライン実行 |
| `pull_request.opened` | `issue-{N}` ブランチ | Intent Drift 検出 |

**Webhook フォワーディング（smee.io）:**

```bash
# 1. smee.io チャネルを作成（ブラウザで https://smee.io にアクセス）
# 2. ローカルにフォワード
npx smee-client --url https://smee.io/YOUR_CHANNEL \
  --target http://localhost:8080/webhooks/github

# 3. GitHub リポジトリ → Settings → Webhooks で smee.io URL を登録
#    Content type: application/json
#    Events: Issues, Issue comments, Pull requests
```

## Quick Start: 動作確認ガイド

### Step 1: ビルド

```bash
mvn clean package -DskipTests
```

### Step 2: Dry-run で Issue 作成を確認

サンプル会議録からIssueを生成（実際にはIssueは作成されない）：

```bash
export GITHUB_TOKEN="your-github-token"

java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  -r kohei3110/talk-to-issue -w . --dry-run \
  compile -f examples/meeting-transcript.txt
```

### Step 3: 複数コンテキストを集約

会議録とインシデントレポートを同時に解析：

```bash
# インライン指定
java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  -r kohei3110/talk-to-issue -w . --dry-run \
  compile --context file:examples/meeting-transcript.txt,file:examples/incident-report.txt

# YAML設定ファイル経由
java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  -r kohei3110/talk-to-issue -w . --dry-run \
  compile --context-config examples/context-config.yaml
```

### Step 4: サーバーモードの起動

```bash
java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  -r kohei3110/talk-to-issue -w . --dry-run \
  serve --port 8080
```

別ターミナルで確認：

```bash
# ヘルスチェック
curl http://localhost:8080/health
# → {"status":"ok","pending_tasks":0}

# issues.labeled イベントのシミュレーション
curl -X POST http://localhost:8080/webhooks/github \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: issues" \
  -d '{
    "action": "labeled",
    "label": {"name": "agent-ready"},
    "issue": {"number": 1, "title": "Test issue"}
  }'
# → 202 Accepted（バックグラウンドで処理開始）

# /pipeline コマンドのシミュレーション
curl -X POST http://localhost:8080/webhooks/github \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: issue_comment" \
  -d '{
    "action": "created",
    "comment": {"body": "/pipeline"},
    "issue": {"number": 1}
  }'
# → 202 Accepted

# PR opened による drift detection のシミュレーション
curl -X POST http://localhost:8080/webhooks/github \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: pull_request" \
  -d '{
    "action": "opened",
    "pull_request": {
      "number": 10,
      "head": {"ref": "issue-1"}
    }
  }'
# → 202 Accepted
```

### Step 5: Webhook 署名検証の確認

```bash
# secret 付きで起動
java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  -r kohei3110/talk-to-issue -w . --dry-run \
  serve --port 8080 --webhook-secret mysecret

# 署名なしリクエスト → 401
curl -X POST http://localhost:8080/webhooks/github \
  -H "X-GitHub-Event: ping" \
  -d '{}'
# → 401 Invalid signature
```

### Step 6: 定期ポーリング付きサーバー

```bash
java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  -r kohei3110/talk-to-issue -w . --dry-run \
  serve --port 8080 --context-config examples/serve-config.yaml --poll-interval 60
# → 60分ごとに needs-triage ラベルの Issue をチェック
```

## Examples

| File | Description |
|------|-------------|
| [examples/meeting-transcript.txt](examples/meeting-transcript.txt) | サンプル会議録（Weekly Engineering Sync） |
| [examples/incident-report.txt](examples/incident-report.txt) | サンプルインシデントレポート（API レスポンスタイム劣化） |
| [examples/feature-request.md](examples/feature-request.md) | サンプル機能要件書（リアルタイム通知機能） |
| [examples/context-config.yaml](examples/context-config.yaml) | コンテキスト設定ファイル例（YAML） |
| [examples/serve-config.yaml](examples/serve-config.yaml) | サーバーモード用ポーリング設定例 |

## Project Structure

```
src/main/java/com/github/talktoissue/
├── App.java                          # CLI エントリポイント (picocli)
├── IssueCompilerSession.java         # コンテキスト → Issue 変換
├── IssueCreationSession.java         # Issue 作成 + 実装
├── IssueQualityScorerSession.java    # Issue 品質採点
├── IntentDriftDetectorSession.java   # PR Intent Drift 検出
├── ImplementationSession.java        # コード実装セッション
├── TranscriptFetchSession.java       # Work IQ トランスクリプト取得
├── commands/
│   ├── CompileCommand.java           # compile サブコマンド
│   ├── ScoreCommand.java             # score サブコマンド
│   ├── DriftCommand.java             # drift サブコマンド
│   ├── RunCommand.java               # run サブコマンド
│   ├── PipelineCommand.java          # pipeline サブコマンド
│   └── ServeCommand.java             # serve サブコマンド（サーバーモード）
├── context/
│   ├── ContextSource.java            # コンテキストソースインターフェース
│   ├── ContextAggregator.java        # 複数ソースの並列集約
│   ├── ContextConfig.java            # YAML 設定パーサー
│   ├── FileContextSource.java        # ローカルファイル読み込み
│   ├── GitHubContextSource.java      # GitHub Issues/PRs 取得
│   ├── WorkIQContextSource.java      # Microsoft 365 データ取得
│   └── MCPContextSource.java         # MCP サーバー経由コンテキスト
├── server/
│   ├── AgentServer.java              # Javalin HTTP サーバー
│   ├── WebhookValidator.java         # HMAC-SHA256 署名検証
│   ├── EventRouter.java              # Webhook イベントルーティング
│   ├── WorkQueue.java                # 非同期ジョブキュー
│   └── Scheduler.java                # 定期ポーリングスケジューラ
└── tools/
    ├── CreateIssueTool.java           # Issue 作成ツール
    ├── CreateBranchTool.java          # ブランチ作成ツール
    ├── CommitAndPushTool.java         # コミット＆プッシュツール
    ├── CreatePullRequestTool.java     # PR 作成ツール
    ├── GetIssueTool.java              # Issue 取得ツール
    ├── GetPullRequestDiffTool.java    # PR diff 取得ツール
    ├── ListLabelsTool.java            # ラベル一覧取得ツール
    ├── ReportDriftTool.java           # ドリフトレポートツール
    ├── ReportQualityScoreTool.java    # 品質スコアレポートツール
    └── ReportTranscriptTool.java      # トランスクリプトレポートツール
```

## License

MIT
