package com.github.talktoissue.context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubContextSourceTest {

    @Mock GHRepository repository;

    @Test
    void getTypeReturnsGitHub() {
        var source = new GitHubContextSource(repository, GitHubContextSource.Scope.ISSUES, null);
        assertEquals("github", source.getType());
    }

    @Test
    void describeContainsRepoName() {
        when(repository.getFullName()).thenReturn("owner/repo");
        var source = new GitHubContextSource(repository, GitHubContextSource.Scope.ISSUES, null);
        assertTrue(source.describe().contains("owner/repo"));
        assertTrue(source.describe().contains("ISSUES"));
    }

    @Test
    void describeIncludesFilter() {
        when(repository.getFullName()).thenReturn("owner/repo");
        var source = new GitHubContextSource(repository, GitHubContextSource.Scope.ISSUES, "label:bug");
        assertTrue(source.describe().contains("label:bug"));
    }

    @Test
    void fetchIssuesExcludesPullRequests() throws Exception {
        var issue = mock(GHIssue.class);
        when(issue.isPullRequest()).thenReturn(false);
        when(issue.getNumber()).thenReturn(1);
        when(issue.getTitle()).thenReturn("Bug report");
        when(issue.getBody()).thenReturn("Description");
        when(issue.getLabels()).thenReturn(List.of());

        var pr = mock(GHIssue.class);
        when(pr.isPullRequest()).thenReturn(true);

        when(repository.getIssues(GHIssueState.OPEN)).thenReturn(List.of(issue, pr));

        var source = new GitHubContextSource(repository, GitHubContextSource.Scope.ISSUES, null);
        String result = source.fetch();

        assertTrue(result.contains("Bug report"));
        assertTrue(result.contains("#1"));
    }

    @Test
    void fetchIssuesWithLabelFilter() throws Exception {
        var matchingIssue = mock(GHIssue.class);
        when(matchingIssue.isPullRequest()).thenReturn(false);
        var matchingLabel = mock(GHLabel.class);
        when(matchingLabel.getName()).thenReturn("bug");
        when(matchingIssue.getLabels()).thenReturn(List.of(matchingLabel));
        when(matchingIssue.getNumber()).thenReturn(1);
        when(matchingIssue.getTitle()).thenReturn("Matching");
        when(matchingIssue.getBody()).thenReturn("yes");

        var nonMatchingIssue = mock(GHIssue.class);
        when(nonMatchingIssue.isPullRequest()).thenReturn(false);
        var otherLabel = mock(GHLabel.class);
        when(otherLabel.getName()).thenReturn("enhancement");
        when(nonMatchingIssue.getLabels()).thenReturn(List.of(otherLabel));

        when(repository.getIssues(GHIssueState.OPEN)).thenReturn(List.of(matchingIssue, nonMatchingIssue));

        var source = new GitHubContextSource(repository, GitHubContextSource.Scope.ISSUES, "label:bug");
        String result = source.fetch();

        assertTrue(result.contains("Matching"));
        assertFalse(result.contains("NonMatching"));
    }

    @Test
    void fetchPullRequests() throws Exception {
        var pr = mock(GHPullRequest.class);
        when(pr.getNumber()).thenReturn(10);
        when(pr.getTitle()).thenReturn("Add feature");
        when(pr.getBody()).thenReturn("PR body");

        when(repository.getPullRequests(GHIssueState.OPEN)).thenReturn(List.of(pr));

        var source = new GitHubContextSource(repository, GitHubContextSource.Scope.PULL_REQUESTS, null);
        String result = source.fetch();

        assertTrue(result.contains("PR #10"));
        assertTrue(result.contains("Add feature"));
    }

    @Test
    void issueWithNullBody() throws Exception {
        var issue = mock(GHIssue.class);
        when(issue.isPullRequest()).thenReturn(false);
        when(issue.getNumber()).thenReturn(5);
        when(issue.getTitle()).thenReturn("No body");
        when(issue.getBody()).thenReturn(null);
        when(issue.getLabels()).thenReturn(List.of());

        when(repository.getIssues(GHIssueState.OPEN)).thenReturn(List.of(issue));

        var source = new GitHubContextSource(repository, GitHubContextSource.Scope.ISSUES, null);
        String result = source.fetch();

        assertTrue(result.contains("No body"));
    }
}
