package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ReportDriftTool {

    public record Drift(String type, String severity, String file, String description) {}

    public record RequirementCoverage(String requirement, String status, String evidence) {}

    public record DriftReport(
        String verdict,
        String overallSummary,
        List<Drift> drifts,
        List<RequirementCoverage> coverageOfRequirements,
        List<String> suggestions
    ) {}

    private volatile DriftReport driftReport;

    public DriftReport getDriftReport() {
        return driftReport;
    }

    @SuppressWarnings("unchecked")
    public ToolDefinition build() {
        return ToolDefinition.create(
            "report_drift",
            "Report the intent drift analysis results. "
                + "Call this tool ONCE after you have fully compared the PR changes against the issue requirements and meeting decisions.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "verdict", Map.of("type", "string", "enum", List.of("pass", "warn", "fail"),
                        "description", "Overall verdict: pass (no drift), warn (minor drift), fail (significant drift)"),
                    "overallSummary", Map.of("type", "string",
                        "description", "Brief summary of the drift analysis findings"),
                    "drifts", Map.of("type", "array", "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "type", Map.of("type", "string", "enum",
                                List.of("scope_creep", "missing_requirement", "approach_divergence", "unrelated_change"),
                                "description", "Type of drift detected"),
                            "severity", Map.of("type", "string", "enum", List.of("low", "medium", "high"),
                                "description", "Severity of this drift"),
                            "file", Map.of("type", "string", "description", "Affected file path (if applicable)"),
                            "description", Map.of("type", "string", "description", "Detailed description of the drift")
                        ),
                        "required", List.of("type", "severity", "description")
                    ), "description", "List of detected drifts"),
                    "coverageOfRequirements", Map.of("type", "array", "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "requirement", Map.of("type", "string", "description", "The requirement from the issue"),
                            "status", Map.of("type", "string", "enum", List.of("met", "partial", "unmet"),
                                "description", "Whether the requirement is met by the PR"),
                            "evidence", Map.of("type", "string", "description", "Evidence from the PR diff")
                        ),
                        "required", List.of("requirement", "status", "evidence")
                    ), "description", "Coverage analysis of each issue requirement"),
                    "suggestions", Map.of("type", "array", "items", Map.of("type", "string"),
                        "description", "Actionable suggestions to align the PR with the original intent")
                ),
                "required", List.of("verdict", "overallSummary", "drifts", "coverageOfRequirements", "suggestions")
            ),
            invocation -> {
                var args = invocation.getArguments();
                String verdict = (String) args.get("verdict");
                String overallSummary = (String) args.get("overallSummary");

                List<Map<String, Object>> rawDrifts = (List<Map<String, Object>>) args.get("drifts");
                List<Drift> drifts = rawDrifts.stream()
                    .map(d -> new Drift(
                        (String) d.get("type"),
                        (String) d.get("severity"),
                        (String) d.getOrDefault("file", ""),
                        (String) d.get("description")
                    ))
                    .toList();

                List<Map<String, Object>> rawCoverage = (List<Map<String, Object>>) args.get("coverageOfRequirements");
                List<RequirementCoverage> coverage = rawCoverage.stream()
                    .map(c -> new RequirementCoverage(
                        (String) c.get("requirement"),
                        (String) c.get("status"),
                        (String) c.get("evidence")
                    ))
                    .toList();

                List<String> suggestions = (List<String>) args.get("suggestions");

                driftReport = new DriftReport(verdict, overallSummary, drifts, coverage, suggestions);

                // Print summary
                String icon = switch (verdict) {
                    case "pass" -> "✓";
                    case "warn" -> "⚠";
                    case "fail" -> "✗";
                    default -> "?";
                };
                System.out.println("Drift Analysis: " + icon + " " + verdict.toUpperCase());
                System.out.println("  " + overallSummary);
                if (!drifts.isEmpty()) {
                    System.out.println("  Drifts detected: " + drifts.size());
                    for (var d : drifts) {
                        System.out.println("    [" + d.severity().toUpperCase() + "] " + d.type() + ": " + d.description());
                    }
                }
                System.out.println("  Requirements coverage: " + coverage.size());
                for (var c : coverage) {
                    System.out.println("    " + c.status().toUpperCase() + ": " + c.requirement());
                }

                return CompletableFuture.completedFuture(Map.of(
                    "status", "received",
                    "verdict", verdict,
                    "driftCount", drifts.size(),
                    "requirementCount", coverage.size()
                ));
            }
        );
    }
}
