package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.SearchHit;
import io.github.lucablum.knowledge.domain.Topic;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Vector search over the Collection's Chunks. */
@PrimaryPort
public class SearchChunks {

    private final EmbeddingPort embedding;
    private final IndexRepository repository;
    private final int maxK;

    public SearchChunks(EmbeddingPort embedding, IndexRepository repository, int maxK) {
        this.embedding = embedding;
        this.repository = repository;
        this.maxK = maxK;
    }

    /** The {@code k} Chunks closest in meaning to {@code query}. */
    public List<SearchHit> search(String query, int k) {
        validate(query, k);
        return repository.nearest(embedding.embed(query), k);
    }

    /** The {@code k} Chunks within {@code topic} closest in meaning to {@code query}. */
    public List<SearchHit> search(String query, Topic topic, int k) {
        validate(query, k);
        return repository.nearest(embedding.embed(query), topic, k);
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
