package io.github.lucablum.knowledge.evaluation;

import io.github.lucablum.knowledge.domain.SearchHit;
import java.util.List;

/**
 * The vector hits a Negative Question still got. Their similarity shows how high a Minimum Similarity would have to
 * be to turn them away.
 */
record NegativeResult(NegativeQuestion question, List<SearchHit> hits) {

    NegativeResult {
        hits = List.copyOf(hits);
    }
}
