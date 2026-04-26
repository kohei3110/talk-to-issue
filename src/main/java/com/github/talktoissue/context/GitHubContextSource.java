package com.github.talktoissue.context;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;

import java.util.List;

/**
 * Fetches context from GitHub Issues, PRs, or Discussions.
 */
public class GitHubContextSource implements ContextSource {

    public enum Scope { ISSUES, PULL_REQUESTS }

    private final GHRepository repository;
    private final Scope scope;
    private final String filter;

    public GitHubContextSource(GHRepository repository, Scope scope, String filter) {
        this.repository = repository;
        this.scope = scope;
        this.filter = filter;
    }

    @Override
    public String getType() {
        return "github";
    }

    @Override
    public String describe() {
        return "GitHub " + scope + " from " + repository.getFullName()
            + (filter != null ? " (filter: " + filter + ")" : "");
    }

    @Override
    public String fetch() throws Exception {
        return switch (scope) {
            case ISSUES -> fetchIssues();
            case PULL_REQUESTS -> fetchPullRequests();
        };
    }

    private String fetchIssues() throws Exception {
        var sb = new StringBuilder();
        List<GHIssue> issues = repository.getIssues(GHIssueState.OPEN);

        for (var issue : issues) {
            if (issue.isPullRequest()) continue;

            boolean matchesFilter = filter == null || matchesLabel(issue);
            if (!matchesFilter) continue;

            sb.append("### Issue #").append(issue.getNumber())
              .append(": ").append(issue.getTitle()).append("\n");
            if (issue.getBody() != null) {
                sb.append(issue.getBody()).append("\n");
            }
            sb.append("Labels: ").append(
                issue.getLabels().stream()
                    .map(l -> l.getName())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("none")
            ).append("\n\n");
        }

        return sb.toString();
    }

    private String fetchPullRequests() throws Exception {
        var sb = new StringBuilder();
        var prs = repository.getPullRequests(org.kohsuke.github.GHIssueState.OPEN);

        for (var pr : prs) {
            sb.append("### PR #").append(pr.getNumber())
              .append(": ").append(pr.getTitle()).append("\n");
            if (pr.getBody() != null) {
                sb.append(pr.getBody()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private boolean matchesLabel(GHIssue issue) {
        if (filter == null) return true;
        /*
         * Label-based filter: "label:xxx"
         * If the filter string is in the form 'label:xxx', only issues/PRs that have a label named 'xxx' will match.
         * Example: filter = "label:bug" → only issues/PRs with the 'bug' label are included.
         * This is a simple filter for single-label matching. For future extensibility, more complex filter formats (e.g., multiple labels, logical operators) could be supported.
         * Supported labels: agent-ready, autonomous, bug, documentation, duplicate, enhancement, error_handling, good first issue, help wanted, invalid, question, security, test_gap, todo, wontfix
         */
        if (filter.startsWith("label:")) {
            String labelName = filter.substring("label:".length()).trim();
            return issue.getLabels().stream()
                .anyMatch(l -> l.getName().equalsIgnoreCase(labelName));
        }
        return true;
    }
}
