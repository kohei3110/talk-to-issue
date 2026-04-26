package com.github.talktoissue.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesFileSourceFromYAML() throws Exception {
        String yaml = """
            sources:
              - type: file
                path: ./notes.txt
            """;
        Path configFile = tempDir.resolve("config.yaml");
        Files.writeString(configFile, yaml);

        var sources = ContextConfig.load(configFile, null, "gpt-4o", null);
        assertEquals(1, sources.size());
        assertTrue(sources.get(0) instanceof FileContextSource);
        assertEquals("file", sources.get(0).getType());
    }

    @Test
    void parsesMultipleSources() throws Exception {
        String yaml = """
            sources:
              - type: file
                path: ./a.txt
              - type: file
                path: ./b.txt
            """;
        Path configFile = tempDir.resolve("multi.yaml");
        Files.writeString(configFile, yaml);

        var sources = ContextConfig.load(configFile, null, "gpt-4o", null);
        assertEquals(2, sources.size());
    }

    @Test
    void parsesWorkIQSource() throws Exception {
        String yaml = """
            sources:
              - type: workiq
                query: "summarize meetings"
                tenant-id: "test-tenant"
            """;
        Path configFile = tempDir.resolve("workiq.yaml");
        Files.writeString(configFile, yaml);

        var sources = ContextConfig.load(configFile, null, "gpt-4o", null);
        assertEquals(1, sources.size());
        assertEquals("workiq", sources.get(0).getType());
    }

    @Test
    void emptySources() throws Exception {
        String yaml = """
            sources: []
            """;
        Path configFile = tempDir.resolve("empty.yaml");
        Files.writeString(configFile, yaml);

        var sources = ContextConfig.load(configFile, null, "gpt-4o", null);
        assertTrue(sources.isEmpty());
    }

    @Test
    void sourceDefFieldsParsed() throws Exception {
        var mapper = new ObjectMapper(new YAMLFactory());
        String yaml = """
            sources:
              - type: mcp
                server: filesystem
                command: npx
                args: ["-y", "@modelcontextprotocol/server-filesystem"]
                query: "Read docs"
            """;
        var config = mapper.readValue(yaml, ContextConfig.Config.class);

        assertEquals(1, config.sources.size());
        var def = config.sources.get(0);
        assertEquals("mcp", def.type);
        assertEquals("filesystem", def.server);
        assertEquals("npx", def.command);
        assertEquals(List.of("-y", "@modelcontextprotocol/server-filesystem"), def.args);
        assertEquals("Read docs", def.query);
    }

    @Test
    void sourceDefGitHubFields() throws Exception {
        var mapper = new ObjectMapper(new YAMLFactory());
        String yaml = """
            sources:
              - type: github
                scope: issues
                filter: "label:bug"
            """;
        var config = mapper.readValue(yaml, ContextConfig.Config.class);

        var def = config.sources.get(0);
        assertEquals("github", def.type);
        assertEquals("issues", def.scope);
        assertEquals("label:bug", def.filter);
    }
}
