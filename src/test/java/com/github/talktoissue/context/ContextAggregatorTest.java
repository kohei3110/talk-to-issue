package com.github.talktoissue.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextAggregatorTest {

    @Test
    void emptySourcesReturnsEmpty() {
        var aggregator = new ContextAggregator(List.of());
        assertEquals("", aggregator.aggregate());
    }

    @Test
    void singleSourceWrappedInTags() {
        var source = new StubContextSource("file", "test.txt", "Hello World");
        var aggregator = new ContextAggregator(List.of(source));

        String result = aggregator.aggregate();

        assertTrue(result.contains("<context source=\"test.txt\" type=\"file\">"));
        assertTrue(result.contains("Hello World"));
        assertTrue(result.contains("</context>"));
    }

    @Test
    void multipleSourcesCombined() {
        var source1 = new StubContextSource("file", "a.txt", "Content A");
        var source2 = new StubContextSource("github", "repo/issues", "Content B");
        var aggregator = new ContextAggregator(List.of(source1, source2));

        String result = aggregator.aggregate();

        assertTrue(result.contains("Content A"));
        assertTrue(result.contains("Content B"));
        assertTrue(result.contains("<context source=\"a.txt\" type=\"file\">"));
        assertTrue(result.contains("<context source=\"repo/issues\" type=\"github\">"));
    }

    @Test
    void failingSourceSkipped() {
        var good = new StubContextSource("file", "ok.txt", "Good content");
        var bad = new FailingContextSource();
        var aggregator = new ContextAggregator(List.of(good, bad));

        String result = aggregator.aggregate();

        assertTrue(result.contains("Good content"));
        assertFalse(result.contains("error"));
    }

    @Test
    void emptyContentSkipped() {
        var empty = new StubContextSource("file", "empty.txt", "   ");
        var nonEmpty = new StubContextSource("file", "data.txt", "Data");
        var aggregator = new ContextAggregator(List.of(empty, nonEmpty));

        String result = aggregator.aggregate();

        assertTrue(result.contains("Data"));
        assertFalse(result.contains("empty.txt"));
    }

    @Test
    void nullContentSkipped() {
        var nullContent = new StubContextSource("file", "null.txt", null);
        var good = new StubContextSource("file", "good.txt", "Good");
        var aggregator = new ContextAggregator(List.of(nullContent, good));

        String result = aggregator.aggregate();

        assertTrue(result.contains("Good"));
    }

    @Test
    void contextTagFormat() {
        var source = new StubContextSource("mcp", "MCP server 'fs': query", "MCP result");
        var aggregator = new ContextAggregator(List.of(source));

        String result = aggregator.aggregate();

        assertTrue(result.startsWith("<context source=\"MCP server 'fs': query\" type=\"mcp\">"));
        assertTrue(result.endsWith("</context>"));
    }

    @Test
    void contentIsStripped() {
        var source = new StubContextSource("file", "padded.txt", "\n  Content with whitespace  \n\n");
        var aggregator = new ContextAggregator(List.of(source));

        String result = aggregator.aggregate();

        assertTrue(result.contains("Content with whitespace"));
    }

    // --- Test helpers ---

    private static class StubContextSource implements ContextSource {
        private final String type;
        private final String description;
        private final String content;

        StubContextSource(String type, String description, String content) {
            this.type = type;
            this.description = description;
            this.content = content;
        }

        @Override
        public String getType() { return type; }

        @Override
        public String describe() { return description; }

        @Override
        public String fetch() { return content; }
    }

    private static class FailingContextSource implements ContextSource {
        @Override
        public String getType() { return "failing"; }

        @Override
        public String describe() { return "always fails"; }

        @Override
        public String fetch() throws Exception {
            throw new RuntimeException("Simulated failure");
        }
    }
}
