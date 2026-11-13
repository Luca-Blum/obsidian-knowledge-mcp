package io.github.lucablum.knowledge.application.search;

import io.github.lucablum.knowledge.application.Embedding;
import io.github.lucablum.knowledge.domain.search.ReciprocalRankFusion;
import io.github.lucablum.knowledge.domain.search.SearchHit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jmolecules.architecture.hexagonal.Application;

/**
 * Finds Chunks twice, by the cosine distance between their vectors and the embedded query and by their full text,
 * and fuses both rankings. Vector candidates below the Minimum Similarity are left out before the fusion, so a
 * question with nothing relevant in the Collection gets only what the full-text search matches; the fused score is
 * never thresholded (ADR 0002). Each Note gets at most a fixed number of Search Hits, and each Search Hit says how
 * many more candidates its Note had.
 */
@Application
public class HybridSearch implements SearchChunks {

    private final Embedding embedding;
    private final ChunkVectors vectors;
    private final ChunkFullText fullText;
    private final ReciprocalRankFusion fusion;
    private final SearchSettings settings;

    public HybridSearch(Embedding embedding, ChunkVectors vectors, ChunkFullText fullText,
            ReciprocalRankFusion fusion, SearchSettings settings) {
        this.embedding = embedding;
        this.vectors = vectors;
        this.fullText = fullText;
        this.fusion = fusion;
        this.settings = settings;
    }

    @Override
    public List<SearchHit> search(SearchRequest request) {
        int k = request.k().orElse(settings.defaultK());
        validate(request.query(), k);
        List<SearchHit> candidates = fusion.fuse(vectorCandidates(request), fullTextCandidates(request));
        return withMoreInNote(capped(candidates, k), candidates);
    }

    /** The closest Chunks that reach the Minimum Similarity, at most as many as the settings allow. */
    private List<SearchHit> vectorCandidates(SearchRequest request) {
        float[] query = embedding.embed(request.query());
        double minimumSimilarity = settings.minimumSimilarity();
        int limit = settings.candidates();
        return request.topic()
                .map(topic -> vectors.nearest(query, topic, minimumSimilarity, limit))
                .orElseGet(() -> vectors.nearest(query, minimumSimilarity, limit));
    }

    /** The best ranked Chunks that contain every term, at most as many as the settings allow. */
    private List<SearchHit> fullTextCandidates(SearchRequest request) {
        String terms = request.fullTextTerms();
        int limit = settings.candidates();
        return request.topic()
                .map(topic -> fullText.matching(terms, topic, limit))
                .orElseGet(() -> fullText.matching(terms, limit));
    }

    /** Each Search Hit learns how many candidates of its Note are not among the Search Hits. */
    private static List<SearchHit> withMoreInNote(List<SearchHit> hits, List<SearchHit> candidates) {
        Map<String, Long> candidatesPerNote = countPerNote(candidates);
        Map<String, Long> hitsPerNote = countPerNote(hits);
        return hits.stream()
                .map(hit -> {
                    String note = hit.notePath();
                    return hit.withMoreInNote(Math.toIntExact(candidatesPerNote.get(note) - hitsPerNote.get(note)));
                })
                .toList();
    }

    /** The first {@code k} candidates, skipping those of a Note that already has its share of Search Hits. */
    private List<SearchHit> capped(List<SearchHit> candidates, int k) {
        Map<String, Integer> hitsPerNote = new HashMap<>();
        List<SearchHit> hits = new ArrayList<>();
        Iterator<SearchHit> remaining = candidates.iterator();
        while (hits.size() < k && remaining.hasNext()) {
            SearchHit candidate = remaining.next();
            if (hitsPerNote.merge(candidate.notePath(), 1, Integer::sum) <= settings.maxHitsPerNote()) {
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
        if (k < 1 || k > settings.maxK()) {
            throw new IllegalArgumentException("k must be between 1 and " + settings.maxK());
        }
    }
}
