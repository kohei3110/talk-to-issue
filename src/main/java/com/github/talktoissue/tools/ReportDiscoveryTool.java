package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ReportDiscoveryTool {

    public record Discovery(
        String category,
        String title,
        String description,
        String severity,
        List<String> affectedFiles,
        String estimatedEffort
    ) {}

    private volatile List<Discovery> discoveries = List.of();

    public List<Discovery> getDiscoveries() {
        return discoveries;
    }

    @SuppressWarnings("unchecked")
    public ToolDefinition build() {
        return ToolDefinition.createSkipPermission(
            "report_discovery",
            "Report discovered improvement opportunities in the codebase. "
                + "Call this tool ONCE after you have fully analyzed the codebase and identified all improvement opportunities.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "discoveries", Map.of("type", "array", "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "category", Map.of("type", "string", "enum",
                                List.of("todo", "test_gap", "security", "tech_debt", "error_handling", "documentation"),
                                "description", "Category of the improvement opportunity"),
                            "title", Map.of("type", "string",
                                "description", "Concise title of the improvement opportunity"),
                            "description", Map.of("type", "string",
                                "description", "Detailed description of what needs to be improved and why"),
                            "severity", Map.of("type", "string", "enum", List.of("high", "medium", "low"),
                                "description", "Severity: high (critical issue), medium (should fix), low (nice to have)"),
                            "affected_files", Map.of("type", "array", "items", Map.of("type", "string"),
                                "description", "List of file paths affected by this issue"),
                            "estimated_effort", Map.of("type", "string", "enum", List.of("small", "medium", "large"),
                                "description", "Estimated effort: small (<1h), medium (1-4h), large (>4h)")
                        ),
                        "required", List.of("category", "title", "description", "severity", "affected_files", "estimated_effort")
                    ), "description", "List of discovered improvement opportunities")
                ),
                "required", List.of("discoveries")
            ),
            invocation -> {
                var args = invocation.getArguments();
                List<Map<String, Object>> rawDiscoveries = (List<Map<String, Object>>) args.get("discoveries");

                discoveries = new ArrayList<>();
                for (var raw : rawDiscoveries) {
                    discoveries.add(new Discovery(
                        (String) raw.get("category"),
                        (String) raw.get("title"),
                        (String) raw.get("description"),
                        (String) raw.get("severity"),
                        raw.get("affected_files") != null
                            ? ((List<?>) raw.get("affected_files")).stream().map(Object::toString).toList()
                            : List.of(),
                        (String) raw.get("estimated_effort")
                    ));
                }

                System.out.println("Discovered " + discoveries.size() + " improvement opportunity(ies):");
                for (var d : discoveries) {
                    System.out.println("  [" + d.severity().toUpperCase() + "] " + d.category() + ": " + d.title());
                    System.out.println("    Files: " + String.join(", ", d.affectedFiles()));
                    System.out.println("    Effort: " + d.estimatedEffort());
                }

                return CompletableFuture.completedFuture(Map.of(
                    "status", "received",
                    "count", discoveries.size()
                ));
            }
        );
    }
}
