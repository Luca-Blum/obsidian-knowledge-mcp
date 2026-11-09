package io.github.lucablum.knowledge.application.search;

import io.github.lucablum.knowledge.domain.note.Topic;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * What is asked of a search: the query, optionally restricted to a Topic, optionally limited to {@code k} Search
 * Hits. What is left out is up to the search to decide, so a caller never has to know the Collection's defaults.
 */
@ValueObject
public final class SearchRequest {

    private final String query;
    private final Topic topic;
    private final Integer k;

    private SearchRequest(String query, Topic topic, Integer k) {
        this.query = query;
        this.topic = topic;
        this.k = k;
    }

    public static SearchRequest of(String query) {
        return new SearchRequest(query, null, null);
    }

    public SearchRequest inTopic(Topic topic) {
        return new SearchRequest(query, Objects.requireNonNull(topic), k);
    }

    public SearchRequest withK(int k) {
        return new SearchRequest(query, topic, k);
    }

    public String query() {
        return query;
    }

    public Optional<Topic> topic() {
        return Optional.ofNullable(topic);
    }

    public OptionalInt k() {
        return k == null ? OptionalInt.empty() : OptionalInt.of(k);
    }
}
