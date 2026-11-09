package io.github.lucablum.knowledge.adapter.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.search.SearchChunks;
import io.github.lucablum.knowledge.application.search.SearchRequest;
import io.github.lucablum.knowledge.domain.note.Topic;
import io.github.lucablum.knowledge.domain.search.SearchHit;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolArguments;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

/**
 * Registers the {@code search} tool programmatically, because its description comes from the Collection's
 * configuration and annotation values must be constants.
 */
@ApplicationScoped
@PrimaryAdapter
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
            List<Hit> hits = search.search(request((String) args.get("query"), topic, args.get("k"))).stream()
                    .map(Hit::of)
                    .toList();
            return ToolResponse.success(json.writeValueAsString(Map.of("hits", hits)));
        } catch (IllegalArgumentException e) {
            return ToolResponse.error(e.getMessage());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private SearchRequest request(String query, String topic, Object k) {
        SearchRequest request = SearchRequest.of(query);
        if (topic != null && !topic.isBlank()) {
            request = request.inTopic(new Topic(topic));
        }
        return k == null ? request : request.withK(wholeNumber(k));
    }

    /** A Search Hit as Claude reads it, with the Note Kind as {@code source} or {@code own}. */
    private record Hit(String content, String notePath, List<String> headingPath, String noteKind, double score) {

        static Hit of(SearchHit hit) {
            return new Hit(hit.content(), hit.notePath(), hit.headingPath(), hit.noteKind().label(), hit.score());
        }
    }

    /** A wrong {@code k} is an error rather than the default, so Claude sees it and can correct the call. */
    private int wholeNumber(Object value) {
        if (value instanceof Integer || value instanceof Long) {
            return Math.clamp(((Number) value).longValue(), Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        throw new IllegalArgumentException("k must be a whole number");
    }
}
