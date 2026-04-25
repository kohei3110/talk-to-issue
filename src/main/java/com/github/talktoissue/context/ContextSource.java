package com.github.talktoissue.context;

/**
 * Abstraction for fetching context from various sources (WorkIQ, GitHub, MCP, files, etc.).
 * Each implementation knows how to retrieve and format context for the LLM.
 */
public interface ContextSource {

    /** Source type identifier (e.g., "workiq", "github", "mcp", "file"). */
    String getType();

    /** Human-readable description of this source, included in the LLM prompt metadata. */
    String describe();

    /** Fetch context content from this source. */
    String fetch() throws Exception;
}
