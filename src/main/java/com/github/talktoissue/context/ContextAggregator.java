package com.github.talktoissue.context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Aggregates context from multiple sources in parallel, combining results
 * into a unified format with source metadata tags.
 */
public class ContextAggregator {

    private final List<ContextSource> sources;

    public ContextAggregator(List<ContextSource> sources) {
        this.sources = sources;
    }

    /**
     * Fetch context from all sources in parallel and combine into a single string.
     * Each source's content is wrapped in {@code <context source="..." type="...">} tags.
     * Failing sources are skipped with a warning.
     */
    public String aggregate() {
        if (sources.isEmpty()) {
            return "";
        }

        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(sources.size(), 4)
        );

        try {
            var futures = new ArrayList<CompletableFuture<ContextResult>>();

            for (var source : sources) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        String content = source.fetch();
                        return new ContextResult(source, content, null);
                    } catch (Exception e) {
                        return new ContextResult(source, null, e);
                    }
                }, executor));
            }

            var results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

            var sb = new StringBuilder();
            for (var result : results) {
                if (result.error() != null) {
                    System.err.println("⚠ Context source '" + result.source().getType()
                        + "' failed: " + result.error().getMessage());
                    continue;
                }
                if (result.content() == null || result.content().isBlank()) {
                    System.err.println("⚠ Context source '" + result.source().getType()
                        + "' returned empty content, skipping.");
                    continue;
                }

                sb.append("<context source=\"").append(result.source().describe())
                  .append("\" type=\"").append(result.source().getType()).append("\">\n");
                sb.append(result.content().strip()).append("\n");
                sb.append("</context>\n\n");
            }

            return sb.toString().strip();
        } finally {
            executor.shutdown();
        }
    }

    private record ContextResult(ContextSource source, String content, Exception error) {}
}
