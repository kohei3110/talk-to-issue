package com.github.talktoissue.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.sdk.CopilotClient;
import com.github.talktoissue.ImplementationSession;
import com.github.talktoissue.IssueCompilerSession;
import com.github.talktoissue.IssueQualityScorerSession;
import com.github.talktoissue.VerificationSession;
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

            // Verification loop
            int maxFixAttempts = 3;
            for (int attempt = 0; attempt <= maxFixAttempts; attempt++) {
                System.out.println("[EventRouter] Verifying build/tests for issue #" + issueNumber
                    + (attempt > 0 ? " (fix attempt " + attempt + ")" : ""));
                try {
                    var verifySession = new VerificationSession(client, model, workingDir, dryRun);
                    var result = verifySession.run();

                    if (result.buildSuccess() && result.testsSuccess()) {
                        System.out.println("[EventRouter] Build/tests passed for issue #" + issueNumber);
                        break;
                    }

                    if (attempt >= maxFixAttempts) {
                        System.out.println("[EventRouter] Build/tests still failing after " + maxFixAttempts + " fix attempts");
                        break;
                    }

                    // Self-correction: feed errors back
                    var errorContext = new StringBuilder();
                    if (!result.buildSuccess()) {
                        errorContext.append("## Build Errors\n").append(result.buildOutput()).append("\n\n");
                    }
                    if (!result.testsSuccess()) {
                        errorContext.append("## Test Failures\n").append(result.testOutput()).append("\n\n");
                    }

                    String fixPrompt = issueBody + "\n\n---\n\n# ⚠ Fix these errors:\n\n" + errorContext;
                    var fixSession = new ImplementationSession(client, model, repo, workingDir, dryRun);
                    fixSession.run(issueNumber, issueTitle, fixPrompt);
                } catch (Exception e) {
                    System.err.println("[EventRouter] Verification failed: " + e.getMessage());
                    break;
                }
            }

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
}
