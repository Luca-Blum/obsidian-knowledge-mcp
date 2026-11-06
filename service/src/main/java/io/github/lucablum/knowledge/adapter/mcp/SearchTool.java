package io.github.lucablum.knowledge.adapter.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.SearchChunks;
import io.github.lucablum.knowledge.domain.SearchHit;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolArguments;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registers the {@code search} tool programmatically, because its description comes from the Collection's
 * configuration and annotation values must be constants.
 */
@ApplicationScoped
public class SearchTool {

    private final ToolManager tools;
    private final SearchChunks search;
    private final CollectionConfig.Search config;
    private final ObjectMapper json;

    public SearchTool(ToolManager tools, SearchChunks search, CollectionConfig collection, ObjectMapper json) {
        this.tools = tools;
        this.search = search;
        this.config = collection.search();
        this.json = json;
    }

    void register(@Observes StartupEvent startup) {
        tools.newTool("search")
                .setDescription(config.toolDescription())
                .addArgument("query", "The question in your own words.", true, String.class)
                .addArgument("topic", "Restricts the search to one Topic (first folder below the Vault root).",
                        false, String.class)
                .addArgument("k", "How many Search Hits to return (default %d).".formatted(config.defaultK()),
                        false, Integer.class)
                .setHandler(this::search)
                .register();
    }

    private ToolResponse search(ToolArguments arguments) {
        Map<String, Object> args = arguments.args();
        Optional<String> topic = Optional.ofNullable((String) args.get("topic")).filter(t -> !t.isBlank());
        int k = args.get("k") instanceof Number number ? number.intValue() : config.defaultK();
        try {
            List<SearchHit> hits = search.search((String) args.get("query"), topic, k);
            return ToolResponse.success(json.writeValueAsString(Map.of("hits", hits)));
        } catch (IllegalArgumentException e) {
            return ToolResponse.error(e.getMessage());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
