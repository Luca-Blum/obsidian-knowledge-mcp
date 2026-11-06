package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.SearchHit;
import io.github.lucablum.knowledge.domain.Topic;
import java.util.List;
import java.util.Optional;

/** Vector search over the Collection's Chunks. */
public class SearchChunks {

    private final EmbeddingPort embedding;
    private final IndexRepository repository;

    public SearchChunks(EmbeddingPort embedding, IndexRepository repository) {
        this.embedding = embedding;
        this.repository = repository;
    }

    /** The {@code k} Chunks closest in meaning to {@code query}, optionally restricted to one Topic. */
    public List<SearchHit> search(String query, Optional<String> topic, int k) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (k < 1) {
            throw new IllegalArgumentException("k must be at least 1");
        }
        return repository.nearest(embedding.embed(query), topic.map(Topic::new), k);
    }
}
