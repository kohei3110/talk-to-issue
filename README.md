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

## API Documentation

### 1. Create GitHub Issue
- **Endpoint:** Internal Copilot Tool: `create_issue`
- **Description:** Create a new GitHub issue in the target repository. Used to file action items, tasks, or decisions extracted from the meeting transcript.
- **Request:**
  - `title` (string): Issue title — concise summary of the action item or task
  - `body` (string): Issue body in Markdown. Include context: who is responsible, what needs to be done, deadline if mentioned, and relevant discussion from the transcript
  - `labels` (array of string): Labels to apply (must exist in the repository)
  - `assignees` (array of string): GitHub usernames to assign
- **Response:**
  - `number` (int): Issue number
  - `title` (string): Issue title
  - `url` (string): Issue URL

### 2. List Labels
- **Endpoint:** Internal Copilot Tool: `list_labels`
- **Description:** List all available labels in the target GitHub repository. Use this to choose appropriate labels when creating issues.
- **Request:** _(none)_
- **Response:**
  - Array of objects with:
    - `name` (string): Label name
    - `description` (string): Label description
    - `color` (string): Label color (hex)

### 3. Create Branch
- **Endpoint:** Internal Copilot Tool: `create_branch`
- **Description:** Create a new git branch for the given issue number and switch to it. Branch name follows the pattern 'issue-{number}'.
- **Request:**
  - `issue_number` (int): The GitHub issue number to create a branch for
- **Response:**
  - `status` (string): Status message

### 4. Commit and Push
- **Endpoint:** Internal Copilot Tool: `commit_and_push`
- **Description:** Stage all changes, commit with the given message, and push to the remote origin. Call this after making code changes to persist them.
- **Request:**
  - `message` (string): Git commit message describing the changes
- **Response:**
  - `status` (string): Status message
  - `message` (string): Commit message

### 5. Create Pull Request
- **Endpoint:** Internal Copilot Tool: `create_pull_request`
- **Description:** Create a pull request on GitHub from the current branch to main. Automatically links to the related issue by appending 'Closes #issue_number' to the body.
- **Request:**
  - `title` (string): Pull request title
  - `body` (string): Pull request body in Markdown describing the changes
  - `issue_number` (int): The GitHub issue number this PR resolves
- **Response:**
  - `number` (int): PR number
  - `title` (string): PR title
  - `url` (string): PR URL

## License

MIT
