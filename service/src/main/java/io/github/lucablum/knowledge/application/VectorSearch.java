package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.SearchHit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jmolecules.architecture.hexagonal.Application;

/**
 * Finds Chunks by the cosine distance between their vectors and the embedded query. Chunks below the Minimum
 * Similarity are left out, so a question with nothing relevant in the Collection gets no Search Hits, and each
 * Note gets at most a fixed number of Search Hits. Each Search Hit says how many more Chunks of its Note matched.
 */
@Application
public class VectorSearch implements SearchChunks {

    private final Embedding embedding;
    private final Index index;
    private final int defaultK;
    private final int maxK;
    private final double minimumSimilarity;
    private final int maxHitsPerNote;

    public VectorSearch(
            Embedding embedding, Index index, int defaultK, int maxK, double minimumSimilarity, int maxHitsPerNote) {
        this.embedding = embedding;
        this.index = index;
        this.defaultK = defaultK;
        this.maxK = maxK;
        this.minimumSimilarity = minimumSimilarity;
        this.maxHitsPerNote = maxHitsPerNote;
    }

    @Override
    public List<SearchHit> search(SearchRequest request) {
        int k = request.k().orElse(defaultK);
        validate(request.query(), k);
        float[] query = embedding.embed(request.query());
        List<SearchHit> candidates = request.topic()
                .map(topic -> index.nearest(query, topic, minimumSimilarity))
                .orElseGet(() -> index.nearest(query, minimumSimilarity));
        List<SearchHit> hits = capped(candidates, k);
        Map<String, Long> matchesPerNote = countPerNote(candidates);
        Map<String, Long> hitsPerNote = countPerNote(hits);
        return hits.stream()
                .map(hit -> hit.withMoreInNote(
                        Math.toIntExact(matchesPerNote.get(hit.notePath()) - hitsPerNote.get(hit.notePath()))))
                .toList();
    }

    /** The first {@code k} candidates, skipping those of a Note that already has its share of Search Hits. */
    private List<SearchHit> capped(List<SearchHit> candidates, int k) {
        Map<String, Integer> hitsPerNote = new HashMap<>();
        List<SearchHit> hits = new ArrayList<>();
        for (SearchHit candidate : candidates) {
            if (hits.size() == k) {
                break;
            }
            if (hitsPerNote.merge(candidate.notePath(), 1, Integer::sum) <= maxHitsPerNote) {
                hits.add(candidate);
            }
        }
        return hits;
    }

    private static Map<String, Long> countPerNote(List<SearchHit> hits) {
        return hits.stream().collect(Collectors.groupingBy(SearchHit::notePath, Collectors.counting()));
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
