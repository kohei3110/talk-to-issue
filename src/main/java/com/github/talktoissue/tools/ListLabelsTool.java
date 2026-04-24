package com.github.talktoissue.tools;

import com.github.copilot.sdk.json.ToolDefinition;
import org.kohsuke.github.GHRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ListLabelsTool {

    private final GHRepository repository;

    public ListLabelsTool(GHRepository repository) {
        this.repository = repository;
    }

    public ToolDefinition build() {
        return ToolDefinition.createSkipPermission(
            "list_labels",
            "List all available labels in the target GitHub repository. Use this to choose appropriate labels when creating issues.",
            Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of()
            ),
            invocation -> CompletableFuture.supplyAsync(() -> {
                try {
                    var labels = repository.listLabels().toList();
                    return labels.stream()
                        .map(label -> Map.of(
                            "name", label.getName(),
                            "description", label.getDescription() != null ? label.getDescription() : "",
                            "color", label.getColor()
                        ))
                        .toList();
                } catch (Exception e) {
                    return Map.of("status", "error", "message", e.getMessage());
                }
            })
        );
    }
}
