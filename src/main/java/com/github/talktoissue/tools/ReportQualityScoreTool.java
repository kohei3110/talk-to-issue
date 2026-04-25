package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ReportQualityScoreTool {

    public record Dimension(String name, int score, String feedback) {}

    public record QualityScore(int overallScore, List<Dimension> dimensions, List<String> suggestions) {}

    private volatile QualityScore qualityScore;

    public QualityScore getQualityScore() {
        return qualityScore;
    }

    @SuppressWarnings("unchecked")
    public ToolDefinition build() {
        return ToolDefinition.create(
            "report_quality_score",
            "Report the quality assessment score for a GitHub issue. "
                + "Call this tool ONCE after you have fully evaluated the issue against all quality dimensions.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "overallScore", Map.of("type", "integer", "description",
                        "Overall quality score from 0-100 (weighted average of all dimensions)"),
                    "dimensions", Map.of("type", "array", "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "name", Map.of("type", "string", "description", "Dimension name (e.g., Clarity, Specificity)"),
                            "score", Map.of("type", "integer", "description", "Score for this dimension (0-100)"),
                            "feedback", Map.of("type", "string", "description", "Specific feedback for this dimension")
                        ),
                        "required", List.of("name", "score", "feedback")
                    ), "description", "Scores for each quality dimension"),
                    "suggestions", Map.of("type", "array", "items", Map.of("type", "string"),
                        "description", "Actionable suggestions to improve issue quality")
                ),
                "required", List.of("overallScore", "dimensions", "suggestions")
            ),
            invocation -> {
                var args = invocation.getArguments();
                int overallScore = ((Number) args.get("overallScore")).intValue();

                List<Map<String, Object>> rawDimensions = (List<Map<String, Object>>) args.get("dimensions");
                List<Dimension> dimensions = rawDimensions.stream()
                    .map(d -> new Dimension(
                        (String) d.get("name"),
                        ((Number) d.get("score")).intValue(),
                        (String) d.get("feedback")
                    ))
                    .toList();

                List<String> suggestions = (List<String>) args.get("suggestions");

                qualityScore = new QualityScore(overallScore, dimensions, suggestions);

                System.out.println("Quality Score: " + overallScore + "/100");
                for (var dim : dimensions) {
                    System.out.println("  " + dim.name() + ": " + dim.score() + "/100 — " + dim.feedback());
                }
                if (!suggestions.isEmpty()) {
                    System.out.println("Suggestions:");
                    for (var s : suggestions) {
                        System.out.println("  - " + s);
                    }
                }

                return CompletableFuture.completedFuture(Map.of(
                    "status", "received",
                    "overallScore", overallScore,
                    "dimensionCount", dimensions.size()
                ));
            }
        );
    }
}
