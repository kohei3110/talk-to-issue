package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tool for reporting code review results, including findings and positive aspects.
 *
 * <p>
 * <b>Review Finding Categories:</b>
 * <ul>
 *   <li><b>security</b>: Security vulnerabilities or unsafe practices.</li>
 *   <li><b>bug</b>: Functional bugs or incorrect behavior.</li>
 *   <li><b>performance</b>: Performance bottlenecks or inefficiencies.</li>
 *   <li><b>error_handling</b>: Inadequate or missing error handling, validation, or exception management.</li>
 *   <li><b>edge_case</b>: Issues related to unhandled or rare edge cases.</li>
 *   <li><b>style</b>: Code style, formatting, or naming issues (minor, non-blocking).</li>
 *   <li><b>maintainability</b>: Code that is hard to understand, maintain, or extend.</li>
 * </ul>
 *
 * Only the above categories are currently supported. Remove or update any references to unused categories.
 */
public class ReportCodeReviewTool {

    /**
     * Represents a single finding from the code review.
     *
     * @param severity   Severity of the finding (critical, warning, suggestion)
     * @param category   Category of the finding (see Review Finding Categories above)
     * @param file       Affected file path
     * @param line       Line number (approximate, 0 if unknown)
     * @param description Detailed description of the issue
     * @param suggestion Suggested fix or improvement
     */
    public record ReviewFinding(String severity, String category, String file, int line, String description, String suggestion) {}

    /**
     * Represents the overall code review result.
     *
     * @param verdict   Overall verdict (approve, request_changes, comment)
     * @param summary   Brief summary of the review findings
     * @param findings  List of review findings
     * @param positives Positive aspects of the implementation
     */
    public record CodeReview(
        String verdict,
        String summary,
        List<ReviewFinding> findings,
        List<String> positives
    ) {}

    private volatile CodeReview codeReview;

    public CodeReview getCodeReview() {
        return codeReview;
    }

    @SuppressWarnings("unchecked")
    public ToolDefinition build() {
        return ToolDefinition.create(
            "report_code_review",
            "Report the code review results. "
                + "Call this tool ONCE after you have fully reviewed the PR diff for quality, security, and correctness issues.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "verdict", Map.of("type", "string", "enum", List.of("approve", "request_changes", "comment"),
                        "description", "Overall verdict: approve (no issues), request_changes (issues found that must be fixed), comment (minor suggestions)"),
                    "summary", Map.of("type", "string",
                        "description", "Brief summary of the review findings"),
                    "findings", Map.of("type", "array", "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "severity", Map.of("type", "string", "enum", List.of("critical", "warning", "suggestion"),
                                "description", "Severity: critical (must fix), warning (should fix), suggestion (nice to have)"),
                            "category", Map.of("type", "string", "enum",
                                List.of("security", "bug", "performance", "error_handling", "edge_case", "style", "maintainability"),
                                "description", "Category of the finding (see Javadoc for details)"),
                            "file", Map.of("type", "string", "description", "Affected file path"),
                            "line", Map.of("type", "integer", "description", "Line number (approximate, 0 if unknown)"),
                            "description", Map.of("type", "string", "description", "Detailed description of the issue"),
                            "suggestion", Map.of("type", "string", "description", "Suggested fix or improvement")
                        ),
                        "required", List.of("severity", "category", "file", "description", "suggestion")
                    ), "description", "List of review findings")
                    ,
                    "positives", Map.of("type", "array", "items", Map.of("type", "string"),
                        "description", "Positive aspects of the implementation worth noting")
                ),
                "required", List.of("verdict", "summary", "findings", "positives")
            ),
            invocation -> {
                var args = invocation.getArguments();
                String verdict = (String) args.get("verdict");
                String summary = (String) args.get("summary");

                List<Map<String, Object>> rawFindings = (List<Map<String, Object>>) args.get("findings");
                List<ReviewFinding> findings = rawFindings.stream()
                    .map(f -> new ReviewFinding(
                        (String) f.get("severity"),
                        (String) f.get("category"),
                        (String) f.getOrDefault("file", ""),
                        f.containsKey("line") ? ((Number) f.get("line")).intValue() : 0,
                        (String) f.get("description"),
                        (String) f.get("suggestion")
                    ))
                    .toList();

                List<String> positives = (List<String>) args.get("positives");

                codeReview = new CodeReview(verdict, summary, findings, positives);

                String icon = switch (verdict) {
                    case "approve" -> "✓";
                    case "request_changes" -> "✗";
                    case "comment" -> "💬";
                    default -> "?";
                };
                System.out.println("[CodeReview] " + icon + " " + verdict.toUpperCase() + ": " + summary);
                for (var finding : findings) {
                    System.out.println("  [" + finding.severity() + "/" + finding.category() + "] "
                        + finding.file() + (finding.line() > 0 ? ":" + finding.line() : "")
                        + " — " + finding.description());
                }

                return CompletableFuture.completedFuture(Map.of(
                    "status", "received",
                    "verdict", verdict,
                    "findingCount", findings.size()
                ));
            }
        );
    }
}