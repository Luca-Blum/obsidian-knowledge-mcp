package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.SearchHit;
import io.github.lucablum.knowledge.domain.Topic;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Vector search over the Collection's Chunks. */
@PrimaryPort
public class SearchChunks {

    private final Embedding embedding;
    private final Index index;
    private final int maxK;

    public SearchChunks(Embedding embedding, Index index, int maxK) {
        this.embedding = embedding;
        this.index = index;
        this.maxK = maxK;
    }

    /** The {@code k} Chunks closest in meaning to {@code query}. */
    public List<SearchHit> search(String query, int k) {
        validate(query, k);
        return index.nearest(embedding.embed(query), k);
    }

    /** The {@code k} Chunks within {@code topic} closest in meaning to {@code query}. */
    public List<SearchHit> search(String query, Topic topic, int k) {
        validate(query, k);
        return index.nearest(embedding.embed(query), topic, k);
    }

    private void validate(String query, int k) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (k < 1 || k > maxK) {
            throw new IllegalArgumentException("k must be between 1 and " + maxK);
        }
    }
}
