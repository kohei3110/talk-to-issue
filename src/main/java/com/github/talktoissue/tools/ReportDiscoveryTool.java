package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tool for reporting discovered improvement opportunities in the codebase.
 * <p>
 * This tool defines the structure and reporting mechanism for various categories of improvement
 * opportunities, such as technical debt, test gaps, security issues, and more. It exposes a public API
 * for retrieving discovered items and building a Copilot SDK ToolDefinition for integration.
 *
 * <p>
 * <b>Improvement Categories:</b>
 * <ul>
 *   <li><b>todo</b>: Unimplemented features, unfinished code, or explicit TODOs left in the codebase.</li>
 *   <li><b>test_gap</b>: Missing or insufficient test coverage for important logic or edge cases.</li>
 *   <li><b>security</b>: Security vulnerabilities, unsafe coding practices, or missing security controls.</li>
 *   <li><b>tech_debt</b>: Technical debt, such as poor structure, outdated dependencies, or code that is hard to maintain.</li>
 *   <li><b>error_handling</b>: Inadequate or missing error handling, lack of validation, or improper exception management.</li>
 *   <li><b>documentation</b>: Missing, outdated, or unclear documentation, including code comments and API docs.</li>
 * </ul>
 *
 * Only the above categories are currently supported. Remove or update any references to unused categories.
 */
public class ReportDiscoveryTool {

    /**
     * Represents a discovered improvement opportunity in the codebase.
     *
     * @param category         Category of the improvement opportunity (see Improvement Categories above)
     * @param title            Concise title of the improvement opportunity
     * @param description      Detailed description of what needs to be improved and why
     * @param severity         Severity of the issue (high: critical, medium: should fix, low: nice to have)
     * @param affectedFiles    List of file paths affected by this issue
     * @param estimatedEffort  Estimated effort to address the issue (small: <1h, medium: 1-4h, large: >4h)
     */
    public record Discovery(
        String category,
        String title,
        String description,
        String severity,
        List<String> affectedFiles,
        String estimatedEffort
    ) {}

    private volatile List<Discovery> discoveries = List.of();

    /**
     * Returns the list of discovered improvement opportunities.
     *
     * @return List of {@link Discovery} objects representing improvement opportunities
     */
    public List<Discovery> getDiscoveries() {
        return discoveries;
    }

    /**
     * Builds the Copilot SDK ToolDefinition for reporting discovered improvement opportunities.
     * <p>
     * The returned ToolDefinition can be registered with the Copilot SDK to enable reporting of
     * discovered issues. When invoked, it updates the internal list of discoveries and prints a summary
     * to standard output.
     *
     * <p>
     * <b>Supported categories:</b> todo, test_gap, security, tech_debt, error_handling, documentation
     *
     * @return ToolDefinition for the report_discovery tool
     */
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
                                "description", "Category of the improvement opportunity (see Javadoc for details)"),
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
