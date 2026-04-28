package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tool for reporting the prioritized list of improvement opportunities to implement.
 *
 * <p>
 * <b>Improvement Categories:</b> See ReportDiscoveryTool for the canonical list and descriptions.
 * The category field should match one of: todo, test_gap, security, tech_debt, error_handling, documentation.
 * </p>
 */
public class ReportPrioritizationTool {

    /**
     * Represents a prioritized improvement item.
     *
     * @param title         Title of the improvement item
     * @param description   Detailed description including what to implement
     * @param category      Category from the original discovery (see ReportDiscoveryTool)
     * @param priorityRank  Priority rank (1 = highest priority)
     * @param rationale     Why this item was prioritized — impact, risk, effort analysis
     */
    public record PrioritizedItem(
        String title,
        String description,
        String category,
        int priorityRank,
        String rationale
    ) {}

    private volatile List<PrioritizedItem> selectedItems = List.of();

    public List<PrioritizedItem> getSelectedItems() {
        return selectedItems;
    }

    @SuppressWarnings("unchecked")
    public ToolDefinition build() {
        return ToolDefinition.createSkipPermission(
            "report_prioritization",
            "Report the prioritized list of improvement opportunities to implement. "
                + "Call this tool ONCE after you have evaluated and ranked all discoveries.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "selected_items", Map.of("type", "array", "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "title", Map.of("type", "string",
                                "description", "Title of the improvement item"),
                            "description", Map.of("type", "string",
                                "description", "Detailed description including what to implement"),
                            "category", Map.of("type", "string",
                                "description", "Category from the original discovery (see ReportDiscoveryTool)"),
                            "priority_rank", Map.of("type", "integer",
                                "description", "Priority rank (1 = highest priority)"),
                            "rationale", Map.of("type", "string",
                                "description", "Why this item was prioritized — impact, risk, effort analysis")
                        ),
                        "required", List.of("title", "description", "category", "priority_rank", "rationale")
                    ), "description", "Prioritized and selected improvement items, ordered by priority rank")
                ),
                "required", List.of("selected_items")
            ),
            invocation -> {
                var args = invocation.getArguments();
                List<Map<String, Object>> rawItems = (List<Map<String, Object>>) args.get("selected_items");

                selectedItems = new ArrayList<>();
                for (var raw : rawItems) {
                    selectedItems.add(new PrioritizedItem(
                        (String) raw.get("title"),
                        (String) raw.get("description"),
                        (String) raw.get("category"),
                        ((Number) raw.get("priority_rank")).intValue(),
                        (String) raw.get("rationale")
                    ));
                }

                System.out.println("Prioritized " + selectedItems.size() + " item(s):");
                for (var item : selectedItems) {
                    System.out.println("  #" + item.priorityRank() + " [" + item.category() + "] " + item.title());
                    System.out.println("    Rationale: " + item.rationale());
                }

                return CompletableFuture.completedFuture(Map.of(
                    "status", "received",
                    "count", selectedItems.size()
                ));
            }
        );
    }
}