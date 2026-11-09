package io.github.lucablum.knowledge.evaluation;

import io.github.lucablum.knowledge.domain.search.SearchHit;
import java.util.List;

/**
 * The vector hits a Negative Question still got. Their similarity shows how high a Minimum Similarity would have to
 * be to turn them away.
 */
record NegativeResult(NegativeQuestion question, List<SearchHit> hits) {

    NegativeResult {
        hits = List.copyOf(hits);
    }

    /** A Negative Question passes when none of its hits reaches the Minimum Similarity. */
    boolean passed(double minimumSimilarity) {
        return hits.stream().noneMatch(hit -> hit.score() >= minimumSimilarity);
    }
}
