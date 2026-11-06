package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.SearchHit;
import java.util.List;
import org.jmolecules.architecture.hexagonal.Application;

/** Finds Chunks by the cosine distance between their vectors and the embedded query. */
@Application
public class VectorSearch implements SearchChunks {

    private final Embedding embedding;
    private final Index index;
    private final int defaultK;
    private final int maxK;

    public VectorSearch(Embedding embedding, Index index, int defaultK, int maxK) {
        this.embedding = embedding;
        this.index = index;
        this.defaultK = defaultK;
        this.maxK = maxK;
    }

    @Override
    public List<SearchHit> search(SearchRequest request) {
        int k = request.k().orElse(defaultK);
        validate(request.query(), k);
        float[] query = embedding.embed(request.query());
        return request.topic()
                .map(topic -> index.nearest(query, topic, k))
                .orElseGet(() -> index.nearest(query, k));
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
