package io.github.lucablum.knowledge.application.search;

import io.github.lucablum.knowledge.domain.note.Topic;
import java.util.Optional;
import java.util.OptionalInt;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * What is asked of a search: the query, optionally exact keywords for the full-text search, optionally restricted to
 * a Topic, optionally limited to {@code k} Search Hits. What is left out is up to the search to decide, so a caller
 * never has to know the Collection's defaults. Blank keywords or a blank Topic count as left out, because that is how
 * the AI Agent leaves out an argument it fills in anyway.
 */
@ValueObject
public final class SearchRequest {

    private final String query;
    private final String keywords;
    private final Topic topic;
    private final Integer k;

    private SearchRequest(String query, String keywords, Topic topic, Integer k) {
        this.query = query;
        this.keywords = keywords;
        this.topic = topic;
        this.k = k;
    }

    public static SearchRequest of(String query) {
        return new SearchRequest(query, null, null, null);
    }

    public SearchRequest withKeywords(String keywords) {
        return isLeftOut(keywords) ? this : new SearchRequest(query, keywords, topic, k);
    }

    public SearchRequest inTopic(String topic) {
        return isLeftOut(topic) ? this : new SearchRequest(query, keywords, new Topic(topic), k);
    }

    public SearchRequest withK(int k) {
        return new SearchRequest(query, keywords, topic, k);
    }

    public String query() {
        return query;
    }

    public Optional<String> keywords() {
        return Optional.ofNullable(keywords);
    }

    /** What the full-text search looks for: the keywords, or else the query. */
    public String fullTextTerms() {
        return keywords().orElse(query);
    }

    public Optional<Topic> topic() {
        return Optional.ofNullable(topic);
    }

    public OptionalInt k() {
        return k == null ? OptionalInt.empty() : OptionalInt.of(k);
    }

    private static boolean isLeftOut(String argument) {
        return argument == null || argument.isBlank();
    }
}
