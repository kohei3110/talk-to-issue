package com.github.talktoissue.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.CopilotClient;
import com.github.talktoissue.IssueCompilerSession;
import com.github.talktoissue.IssueQualityScorerSession;
import com.github.talktoissue.ImplementationSession;
import com.github.talktoissue.IntentDriftDetectorSession;
import com.github.talktoissue.context.ContextAggregator;
import com.github.talktoissue.context.ContextConfig;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.File;
import java.nio.file.Path;
/**
 * Routes GitHub webhook events to the appropriate pipeline steps.
 *
 * Supported events:
 * - issues.labeled (trigger label) → quality score → implement
 * - issue_comment.created ("/pipeline" command) → full pipeline on issue context
 * - pull_request.opened → intent drift detection
 */
public class EventRouter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkQueue workQueue;
    private final String repoFullName;
    private final File workingDir;
    private final String model;
    private final boolean dryRun;
    private final String triggerLabel;
    private final Path contextConfig;

    public EventRouter(WorkQueue workQueue, String repoFullName, File workingDir,
                       String model, boolean dryRun, String triggerLabel, Path contextConfig) {
        this.workQueue = workQueue;
        this.repoFullName = repoFullName;
        this.workingDir = workingDir;
        this.model = model;
        this.dryRun = dryRun;
        this.triggerLabel = triggerLabel != null ? triggerLabel : "agent-ready";
        this.contextConfig = contextConfig;
    }

    /**
     * Route an incoming webhook event.
     *
     * @param eventType the X-GitHub-Event header value
     * @param payload   the raw JSON payload
     */
    public void route(String eventType, String payload) {
        try {
            JsonNode root = MAPPER.readTree(payload);
            String action = root.path("action").asText("");

            switch (eventType) {
                case "issues" -> handleIssueEvent(action, root);
                case "issue_comment" -> handleIssueCommentEvent(action, root);
                case "pull_request" -> handlePullRequestEvent(action, root);
                default -> System.out.println("[EventRouter] Ignoring event: " + eventType);
            }
        } catch (Exception e) {
            System.err.println("[EventRouter] Error parsing payload: " + e.getMessage());
        }
    }

    private void handleIssueEvent(String action, JsonNode root) {
        if (!"labeled".equals(action)) return;

        String labelName = root.path("label").path("name").asText("");
        if (!triggerLabel.equals(labelName)) {
            System.out.println("[EventRouter] issues.labeled with '" + labelName + "', ignoring (trigger: " + triggerLabel + ")");
            return;
        }

        int issueNumber = root.path("issue").path("number").asInt();
        String issueTitle = root.path("issue").path("title").asText("");

        System.out.println("[EventRouter] Triggered by label '" + triggerLabel + "' on issue #" + issueNumber);

        workQueue.submit(repoFullName, "implement-issue-" + issueNumber, () -> {
            try {
                executeImplementation(issueNumber, issueTitle);
            } catch (Exception e) {
                throw new RuntimeException("Implementation failed for issue #" + issueNumber, e);
            }
        });
    }

    private void handleIssueCommentEvent(String action, JsonNode root) {
        if (!"created".equals(action)) return;

        String body = root.path("comment").path("body").asText("").strip();
        if (!body.startsWith("/pipeline")) {
            return;
        }

        int issueNumber = root.path("issue").path("number").asInt();
        System.out.println("[EventRouter] /pipeline command on issue #" + issueNumber);

        workQueue.submit(repoFullName, "pipeline-from-comment-" + issueNumber, () -> {
            try {
                executeCompileFromIssue(issueNumber);
            } catch (Exception e) {
                throw new RuntimeException("Pipeline failed for issue #" + issueNumber, e);
            }
        });
    }

    private void handlePullRequestEvent(String action, JsonNode root) {
        if (!"opened".equals(action) && !"synchronize".equals(action)) return;

        int prNumber = root.path("pull_request").path("number").asInt();
        String prBranch = root.path("pull_request").path("head").path("ref").asText("");

        // Extract issue number from branch name (convention: issue-{number})
        if (!prBranch.startsWith("issue-")) {
            System.out.println("[EventRouter] PR #" + prNumber + " branch '" + prBranch + "' doesn't follow issue-{N} convention, skipping drift check");
            return;
        }

        int issueNumber;
        try {
            issueNumber = Integer.parseInt(prBranch.substring(6));
        } catch (NumberFormatException e) {
            return;
        }

        System.out.println("[EventRouter] PR #" + prNumber + " opened for issue #" + issueNumber + ", running drift detection");

        workQueue.submit(repoFullName, "drift-pr-" + prNumber, () -> {
            try {
                executeDriftDetection(prNumber, issueNumber);
            } catch (Exception e) {
                throw new RuntimeException("Drift detection failed for PR #" + prNumber, e);
            }
        });
    }

    private void executeImplementation(int issueNumber, String issueTitle) throws Exception {
        String token = System.getenv("GITHUB_TOKEN");
        GitHub gitHub = new GitHubBuilder().withOAuthToken(token).build();
        GHRepository repo = gitHub.getRepository(repoFullName);

        try (var client = new CopilotClient()) {
            client.start().get();

            // Score the issue first
            var scorerSession = new IssueQualityScorerSession(client, model, repo, workingDir);
            var score = scorerSession.run(issueNumber);
            System.out.println("[EventRouter] Issue #" + issueNumber + " score: " + score.overallScore());

            if (score.overallScore() < 70) {
                System.out.println("[EventRouter] Issue #" + issueNumber + " below quality threshold, skipping");
                return;
            }

            // Implement
            var ghIssue = repo.getIssue(issueNumber);
            String issueBody = ghIssue.getBody() != null ? ghIssue.getBody() : issueTitle;

            var implSession = new ImplementationSession(client, model, repo, workingDir, dryRun);
            implSession.run(issueNumber, issueTitle, issueBody);
            System.out.println("[EventRouter] Implementation complete for issue #" + issueNumber);
        }
    }

    private void executeCompileFromIssue(int issueNumber) throws Exception {
        String token = System.getenv("GITHUB_TOKEN");
        GitHub gitHub = new GitHubBuilder().withOAuthToken(token).build();
        GHRepository repo = gitHub.getRepository(repoFullName);

        try (var client = new CopilotClient()) {
            client.start().get();

            // Get issue body as context
            var ghIssue = repo.getIssue(issueNumber);
            String issueContext = "# " + ghIssue.getTitle() + "\n\n" + ghIssue.getBody();

            // If there's a context config, aggregate additional context
            String resolvedContext;
            if (contextConfig != null) {
                var sources = ContextConfig.load(contextConfig, client, model, repo);
                sources.add(new com.github.talktoissue.context.FileContextSource(Path.of("/dev/null")) {
                    @Override
                    public String getType() { return "github-issue"; }
                    @Override
                    public String describe() { return "Issue #" + issueNumber; }
                    @Override
                    public String fetch() { return issueContext; }
                });
                resolvedContext = new ContextAggregator(sources).aggregate();
            } else {
                resolvedContext = issueContext;
            }

            var compilerSession = new IssueCompilerSession(client, model, repo, workingDir, dryRun);
            var issues = compilerSession.run(resolvedContext);
            System.out.println("[EventRouter] Compiled " + issues.size() + " issue(s) from issue #" + issueNumber);
        }
    }

    private void executeDriftDetection(int prNumber, int issueNumber) throws Exception {
        String token = System.getenv("GITHUB_TOKEN");
        GitHub gitHub = new GitHubBuilder().withOAuthToken(token).build();
        GHRepository repo = gitHub.getRepository(repoFullName);

        try (var client = new CopilotClient()) {
            client.start().get();

            var ghIssue = repo.getIssue(issueNumber);
            String originalContext = ghIssue.getBody() != null ? ghIssue.getBody() : ghIssue.getTitle();

            var driftSession = new IntentDriftDetectorSession(client, model, repo, workingDir);
            var report = driftSession.run(prNumber, issueNumber, originalContext);

            System.out.println("[EventRouter] Drift detection for PR #" + prNumber
                + ": " + report.verdict() + " (" + report.drifts().size() + " drift(s))");
        }
    }
}
