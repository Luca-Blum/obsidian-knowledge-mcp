package io.github.lucablum.knowledge.application.search;

import io.github.lucablum.knowledge.application.Embedding;
import io.github.lucablum.knowledge.domain.search.SearchHit;
import java.util.List;
import org.jmolecules.architecture.hexagonal.Application;

/** Finds Chunks by the cosine distance between their vectors and the embedded query. */
@Application
public class VectorSearch implements SearchChunks {

    private final Embedding embedding;
    private final ChunkVectors vectors;
    private final int defaultK;
    private final int maxK;

    public VectorSearch(Embedding embedding, ChunkVectors vectors, int defaultK, int maxK) {
        this.embedding = embedding;
        this.vectors = vectors;
        this.defaultK = defaultK;
        this.maxK = maxK;
    }

    @Override
    public List<SearchHit> search(SearchRequest request) {
        int k = request.k().orElse(defaultK);
        validate(request.query(), k);
        float[] query = embedding.embed(request.query());
        return request.topic()
                .map(topic -> vectors.nearest(query, topic, k))
                .orElseGet(() -> vectors.nearest(query, k));
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
