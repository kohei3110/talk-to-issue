# talk-to-issue

Analyze meeting transcripts, automatically create GitHub Issues, implement the code changes, and open Pull Requests — all powered by the [GitHub Copilot SDK for Java](https://github.github.io/copilot-sdk-java/).

## Features

| Feature | Command | Description |
|---------|---------|-------------|
| **Issue Compiler** | `compile` | 会議録をcoding-agent-readyなIssueに変換する。概要・受け入れ基準・技術コンテキスト・スコープ・テスト要件を含む構造化テンプレートで Issue を作成。 |
| **Issue Quality Scorer** | `score` | IssueがAI coding agentに渡せる品質か採点する（0〜100点）。6軸評価: Clarity(20%), Specificity(20%), Acceptance Criteria(20%), Scope(15%), Testability(15%), Context(10%)。 |
| **Intent Drift Detector** | `drift` | PRが会議決定・Issue要件からズレていないか検出する。4種のドリフト（scope_creep, missing_requirement, approach_divergence, unrelated_change）を検出し pass/warn/fail で判定。 |
| **Full Workflow** | `run` | 会議録 → Issue作成 → 実装 → PR作成の一気通貫ワークフロー。 |
| **Full Pipeline** | `pipeline` | compile → score（品質フィルタ） → implement → drift detection のフルパイプライン。 |

## How it works

```
Meeting Transcript
       │
       ▼
  ┌─────────┐     ┌─────────┐     ┌──────────────┐     ┌───────────────┐
  │ compile  │────▶│  score  │────▶│  implement   │────▶│    drift      │
  │ 会議録→  │     │ 品質採点 │     │ Issue→PR     │     │ ズレ検出      │
  │ Issue作成│     │ (0-100) │     │              │     │ pass/warn/fail│
  └─────────┘     └─────────┘     └──────────────┘     └───────────────┘
```

1. **Issue Compiler** (`compile`): LLMが会議録を分析し、コーディングエージェントが即座に着手できる構造化Issueを作成。コードベースの探索も行い、技術コンテキストを自動付与。
2. **Issue Quality Scorer** (`score`): 作成されたIssueを6軸で採点。基準未満のIssueはパイプラインから除外可能。
3. **Implementation** (`run` / `pipeline`): 各Issueに対してブランチ作成 → コード実装 → コミット＆プッシュ → PR作成を自動実行。
4. **Intent Drift Detector** (`drift`): PRの差分をIssue要件・会議録と照合し、スコープクリープや要件漏れを検出。

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

### `compile` — 会議録 → Issue作成

会議録を解析し、構造化されたGitHub Issueを作成します。

```bash
java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  -r owner/repo -w /path/to/clone --dry-run \
  compile -f meeting-notes.txt
```

| Flag | Required | Description |
|------|----------|-------------|
| `-f`, `--file` | * | Path to the meeting transcript file |
| `-q`, `--workiq-query` | * | Natural language query to fetch transcript from Work IQ |
| `--tenant-id` | No | Microsoft Entra tenant ID for Work IQ |

\* Either `--file` or `--workiq-query` must be specified.

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
java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  -r owner/repo -w /path/to/clone --dry-run \
  pipeline -f meeting-notes.txt --min-score 70
```

| Flag | Required | Description |
|------|----------|-------------|
| `-f`, `--file` | * | Path to the meeting transcript file |
| `-q`, `--workiq-query` | * | Natural language query to fetch transcript from Work IQ |
| `--tenant-id` | No | Microsoft Entra tenant ID for Work IQ |
| `--min-score` | No | Minimum quality score to proceed with implementation (default: 70) |

\* Either `--file` or `--workiq-query` must be specified.

## Project structure

```
src/main/java/com/github/talktoissue/
├── App.java                          # CLI entrypoint (picocli parent command)
├── commands/
│   ├── RunCommand.java               # run: full workflow (transcript → Issues → PRs)
│   ├── CompileCommand.java           # compile: transcript → structured Issues
│   ├── ScoreCommand.java             # score: Issue quality scoring (0-100)
│   ├── DriftCommand.java             # drift: PR intent drift detection
│   └── PipelineCommand.java          # pipeline: compile → score → implement → drift
├── IssueCreationSession.java         # Copilot session: transcript → Issues
├── IssueCompilerSession.java         # Copilot session: transcript → structured Issues (enhanced)
├── IssueQualityScorerSession.java    # Copilot session: Issue quality scoring
├── IntentDriftDetectorSession.java   # Copilot session: PR drift detection
├── ImplementationSession.java        # Copilot session: Issue → branch → PR
├── TranscriptFetchSession.java       # Copilot session: Work IQ transcript fetch
└── tools/
    ├── CreateIssueTool.java          # Custom tool: create GitHub Issue
    ├── ListLabelsTool.java           # Custom tool: list repo labels (read-only)
    ├── CreateBranchTool.java         # Custom tool: git checkout -b
    ├── CommitAndPushTool.java        # Custom tool: git add/commit/push
    ├── CreatePullRequestTool.java    # Custom tool: create GitHub PR
    ├── GetIssueTool.java             # Custom tool: fetch Issue details (read-only)
    ├── GetPullRequestDiffTool.java   # Custom tool: fetch PR diff (read-only)
    ├── ReportQualityScoreTool.java   # Custom tool: report quality score
    ├── ReportDriftTool.java          # Custom tool: report drift analysis
    └── ReportTranscriptTool.java     # Custom tool: report fetched transcript
```

## License

MIT
