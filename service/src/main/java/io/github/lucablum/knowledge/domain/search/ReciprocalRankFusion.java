package io.github.lucablum.knowledge.domain.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jmolecules.ddd.annotation.Service;

/**
 * Merges the rankings of the vector and the full-text search into one. Each Chunk scores the sum of
 * {@code weight / (k + rank)} over the rankings it appears in, so only ranks count, never the searches' own scores.
 * A larger {@code k} flattens the difference between the top ranks and the ones below, and a weight of 0 leaves that
 * ranking out entirely.
 */
@Service
public class ReciprocalRankFusion {

    private final int k;
    private final double vectorWeight;
    private final double fullTextWeight;

    public ReciprocalRankFusion(int k, double vectorWeight, double fullTextWeight) {
        if (k < 0 || vectorWeight < 0 || fullTextWeight < 0) {
            throw new IllegalArgumentException("fusion needs k >= 0 and weights >= 0: %d / %s / %s"
                    .formatted(k, vectorWeight, fullTextWeight));
        }
        this.k = k;
        this.vectorWeight = vectorWeight;
        this.fullTextWeight = fullTextWeight;
    }

    /** Both rankings' Search Hits, each Chunk once, highest fused score first. */
    public List<SearchHit> fuse(List<SearchHit> vector, List<SearchHit> fullText) {
        Map<String, SearchHit> chunks = new LinkedHashMap<>();
        Map<String, Double> scores = new LinkedHashMap<>();
        add(vector, vectorWeight, chunks, scores);
        add(fullText, fullTextWeight, chunks, scores);
        List<SearchHit> fused = new ArrayList<>();
        chunks.forEach((chunk, hit) -> fused.add(hit.withScore(scores.get(chunk))));
        fused.sort(Comparator.comparingDouble(SearchHit::score).reversed());
        return fused;
    }

    private void add(List<SearchHit> ranking, double weight, Map<String, SearchHit> chunks, Map<String, Double> scores) {
        if (weight == 0) {
            return;
        }
        for (int i = 0; i < ranking.size(); i++) {
            SearchHit hit = ranking.get(i);
            String chunk = hit.notePath() + "#" + hit.ordinal();
            chunks.merge(chunk, hit, SearchHit::mergedWith);
            scores.merge(chunk, weight / (k + i + 1), Double::sum);
        }
    }
}
