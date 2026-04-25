package com.github.talktoissue.context;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.copilot.sdk.CopilotClient;
import org.kohsuke.github.GHRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads context source definitions from a YAML configuration file.
 *
 * Example YAML:
 * <pre>
 * sources:
 *   - type: file
 *     path: ./meeting-notes.txt
 *   - type: workiq
 *     query: "直近1週間の会議を要約して"
 *     tenant-id: "xxx"
 *   - type: github
 *     scope: issues
 *     filter: "label:needs-triage"
 *   - type: mcp
 *     server: filesystem
 *     command: npx
 *     args: ["-y", "@modelcontextprotocol/server-filesystem", "/docs"]
 *     query: "Read the requirements document"
 * </pre>
 */
public class ContextConfig {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Config {
        @JsonProperty("sources")
        public List<SourceDef> sources = List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SourceDef {
        @JsonProperty("type")
        public String type;

        @JsonProperty("path")
        public String path;

        @JsonProperty("query")
        public String query;

        @JsonProperty("tenant-id")
        public String tenantId;

        @JsonProperty("scope")
        public String scope;

        @JsonProperty("filter")
        public String filter;

        @JsonProperty("server")
        public String server;

        @JsonProperty("command")
        public String command;

        @JsonProperty("args")
        public List<String> args;
    }

    /**
     * Parse a YAML config file and build ContextSource instances.
     *
     * @param configPath   path to the YAML config file
     * @param client       CopilotClient (needed for MCP sources)
     * @param model        LLM model name
     * @param repository   GitHub repository (needed for GitHub sources)
     * @return list of ContextSource instances
     */
    public static List<ContextSource> load(Path configPath, CopilotClient client,
                                           String model, GHRepository repository) throws Exception {
        var mapper = new ObjectMapper(new YAMLFactory());
        var config = mapper.readValue(Files.readString(configPath), Config.class);
        return buildSources(config.sources, client, model, repository);
    }

    /**
     * Build ContextSource instances from parsed source definitions.
     */
    public static List<ContextSource> buildSources(List<SourceDef> defs, CopilotClient client,
                                                    String model, GHRepository repository) {
        var sources = new ArrayList<ContextSource>();
        for (var def : defs) {
            sources.add(buildSource(def, client, model, repository));
        }
        return sources;
    }

    private static ContextSource buildSource(SourceDef def, CopilotClient client,
                                             String model, GHRepository repository) {
        return switch (def.type) {
            case "file" -> new FileContextSource(Path.of(def.path));
            case "workiq" -> new WorkIQContextSource(def.query, def.tenantId);
            case "github" -> {
                var scope = "pull_requests".equals(def.scope)
                    ? GitHubContextSource.Scope.PULL_REQUESTS
                    : GitHubContextSource.Scope.ISSUES;
                yield new GitHubContextSource(repository, scope, def.filter);
            }
            case "mcp" -> new MCPContextSource(
                client, model,
                def.server != null ? def.server : "mcp",
                def.command,
                def.args != null ? def.args : List.of(),
                def.query
            );
            default -> throw new IllegalArgumentException("Unknown context source type: " + def.type);
        };
    }
}
