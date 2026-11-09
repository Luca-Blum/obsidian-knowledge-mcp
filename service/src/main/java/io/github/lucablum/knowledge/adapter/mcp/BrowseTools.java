package io.github.lucablum.knowledge.adapter.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lucablum.knowledge.application.catalog.BrowseCollection;
import io.github.lucablum.knowledge.domain.catalog.SourceNoteListing;
import io.github.lucablum.knowledge.domain.catalog.TopicCount;
import io.github.lucablum.knowledge.domain.note.Topic;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

/** The {@code list_topics} and {@code list_sources} tools, which show Claude what the Collection holds. */
@ApplicationScoped
@PrimaryAdapter
public class BrowseTools {

    private final BrowseCollection browse;
    private final ObjectMapper json;

    public BrowseTools(BrowseCollection browse, ObjectMapper json) {
        this.browse = browse;
        this.json = json;
    }

    @Tool(name = "list_topics",
            description = "Lists the Topics of this Collection (first folders below the Vault root) with their number "
                    + "of Notes, so you can choose a 'topic' for search and list_sources.")
    ToolResponse listTopics() {
        List<TopicEntry> topics = browse.topics().stream().map(TopicEntry::of).toList();
        return success(Map.of("topics", topics));
    }

    @Tool(name = "list_sources",
            description = "Lists the Source Notes of one Topic with Title, date (published, otherwise imported) and "
                    + "author where known, so you can see which sources exist before searching.")
    ToolResponse listSources(@ToolArg(description = "The Topic, as list_topics names it.") String topic) {
        if (topic == null || topic.isBlank()) {
            return ToolResponse.error("topic must not be blank");
        }
        List<SourceEntry> sources = browse.sourceNotes(new Topic(topic)).stream().map(SourceEntry::of).toList();
        return success(Map.of("sources", sources));
    }

    private ToolResponse success(Map<String, ?> result) {
        try {
            return ToolResponse.success(json.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private record TopicEntry(String topic, int notes) {

        static TopicEntry of(TopicCount count) {
            return new TopicEntry(count.topic().name(), count.notes());
        }
    }

    /** A Source Note as Claude reads it; a date or author that is not known is left out rather than guessed. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SourceEntry(String notePath, String title, String date, String author) {

        static SourceEntry of(SourceNoteListing source) {
            return new SourceEntry(
                    source.notePath(), source.title(), source.date().orElse(null), source.author().orElse(null));
        }
    }
}
