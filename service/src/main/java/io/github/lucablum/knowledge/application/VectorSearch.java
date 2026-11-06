package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.SearchHit;
import io.github.lucablum.knowledge.domain.Topic;
import java.util.List;
import org.jmolecules.architecture.hexagonal.Application;

/** Finds Chunks by the cosine distance between their vectors and the embedded query. */
@Application
public class VectorSearch implements SearchChunks {

    private final Embedding embedding;
    private final Index index;
    private final int maxK;

    public VectorSearch(Embedding embedding, Index index, int maxK) {
        this.embedding = embedding;
        this.index = index;
        this.maxK = maxK;
    }

    @Override
    public List<SearchHit> search(String query, int k) {
        validate(query, k);
        return index.nearest(embedding.embed(query), k);
    }

    @Override
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
