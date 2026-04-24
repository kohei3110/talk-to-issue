# talk-to-issue

Analyze meeting transcripts, automatically create GitHub Issues, implement the code changes, and open Pull Requests — all powered by the [GitHub Copilot SDK for Java](https://github.github.io/copilot-sdk-java/).

## How it works

1. **Phase 1 – Issue Creation**: The LLM reads your meeting transcript, extracts action items, and creates a GitHub Issue for each one (with labels and descriptions).
2. **Phase 2 – Implementation**: For every created Issue, a separate LLM session checks out a new branch, uses built-in coding tools to implement the change, commits, pushes, and opens a Pull Request.

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

```bash
export GITHUB_TOKEN=ghp_your_token_here

java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  --file meeting-notes.txt \
  --repo owner/repo \
  --working-dir /path/to/local/clone
```

### Options

| Flag | Required | Description |
|------|----------|-------------|
| `-f`, `--file` | Yes | Path to the meeting transcript file |
| `-r`, `--repo` | Yes | Target GitHub repository (`owner/repo`) |
| `-w`, `--working-dir` | Yes | Path to the local clone of the target repo |
| `-m`, `--model` | No | LLM model (default: `gpt-4.1`) |
| `--dry-run` | No | Simulate without creating issues/PRs |

### Dry run

```bash
java -jar target/talk-to-issue-1.0-SNAPSHOT.jar \
  --file meeting-notes.txt \
  --repo owner/repo \
  --working-dir /path/to/local/clone \
  --dry-run
```

## Project structure

```
src/main/java/com/github/talktoissue/
├── App.java                          # CLI entrypoint (picocli)
├── IssueCreationSession.java         # Phase 1: transcript → Issues
├── ImplementationSession.java        # Phase 2: Issue → branch → PR
└── tools/
    ├── CreateIssueTool.java          # Custom tool: create GitHub Issue
    ├── ListLabelsTool.java           # Custom tool: list repo labels (read-only)
    ├── CreateBranchTool.java         # Custom tool: git checkout -b
    ├── CommitAndPushTool.java        # Custom tool: git add/commit/push
    └── CreatePullRequestTool.java    # Custom tool: create GitHub PR
```

## License

MIT
