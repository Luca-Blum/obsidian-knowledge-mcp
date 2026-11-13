package io.github.lucablum.knowledge.domain.search;

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
        Map<ChunkKey, SearchHit> fused = combined(scoredByRank(vector, vectorWeight),
                scoredByRank(fullText, fullTextWeight));
        return fused.values().stream()
                .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
                .toList();
    }

    /** Each Search Hit of the ranking, scored by its rank alone; none if the ranking's weight is 0. */
    private Map<ChunkKey, SearchHit> scoredByRank(List<SearchHit> ranking, double weight) {
        Map<ChunkKey, SearchHit> scored = new LinkedHashMap<>();
        if (weight == 0) {
            return scored;
        }
        for (int i = 0; i < ranking.size(); i++) {
            int rank = i + 1;
            SearchHit hit = ranking.get(i);
            scored.merge(ChunkKey.of(hit), hit.withScore(weight / (k + rank)), ReciprocalRankFusion::sameChunkCombined);
        }
        return scored;
    }

    /** The Search Hits of both rankings, a Chunk in both combined into one. */
    private static Map<ChunkKey, SearchHit> combined(Map<ChunkKey, SearchHit> first, Map<ChunkKey, SearchHit> second) {
        Map<ChunkKey, SearchHit> combined = new LinkedHashMap<>(first);
        second.forEach((chunk, hit) -> combined.merge(chunk, hit, ReciprocalRankFusion::sameChunkCombined));
        return combined;
    }

    /** The Chunk as both searches know it, scored by the sum of both ranks' scores. */
    private static SearchHit sameChunkCombined(SearchHit earlier, SearchHit later) {
        return earlier.mergedWith(later).withScore(earlier.score() + later.score());
    }

    /** A Chunk is its Note and its position there, so the same Chunk from both searches is fused into one. */
    private record ChunkKey(String notePath, int ordinal) {

        static ChunkKey of(SearchHit hit) {
            return new ChunkKey(hit.notePath(), hit.ordinal());
        }
    }
}
