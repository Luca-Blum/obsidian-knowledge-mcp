package io.github.lucablum.knowledge.adapter.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.SearchChunks;
import io.github.lucablum.knowledge.domain.SearchHit;
import io.github.lucablum.knowledge.domain.Topic;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolArguments;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.List;
import java.util.Map;

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
                .addArgument("k", "How many Search Hits to return (default %d, at most %d)."
                                .formatted(config.defaultK(), config.maxK()),
                        false, Integer.class)
                .setHandler(this::search)
                .register();
    }

    private ToolResponse search(ToolArguments arguments) {
        Map<String, Object> args = arguments.args();
        String topic = (String) args.get("topic");
        try {
            String query = (String) args.get("query");
            int k = k(args.get("k"));
            List<SearchHit> hits = topic == null || topic.isBlank()
                    ? search.search(query, k)
                    : search.search(query, new Topic(topic), k);
            return ToolResponse.success(json.writeValueAsString(Map.of("hits", hits)));
        } catch (IllegalArgumentException e) {
            return ToolResponse.error(e.getMessage());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A wrong {@code k} is an error rather than the default, so Claude sees it and can correct the call. */
    private int k(Object value) {
        if (value == null) {
            return config.defaultK();
        }
        if (value instanceof Integer || value instanceof Long) {
            return Math.clamp(((Number) value).longValue(), Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        throw new IllegalArgumentException("k must be a whole number");
    }
}
