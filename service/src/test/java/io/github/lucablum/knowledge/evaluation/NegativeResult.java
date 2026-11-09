package io.github.lucablum.knowledge.evaluation;

import io.github.lucablum.knowledge.domain.search.SearchHit;
import java.util.List;

/**
 * The Search Hits a Negative Question still got. Those of the vector search decide whether it passes, and their
 * similarity shows how high a Minimum Similarity would have to be to turn them away. Those of the full-text search
 * are reported for information only, to show whether it adds noise.
 */
record NegativeResult(NegativeQuestion question, List<SearchHit> hits) {

    NegativeResult {
        hits = List.copyOf(hits);
    }

    List<SearchHit> vectorHits() {
        return hits.stream().filter(hit -> hit.similarity().isPresent()).toList();
    }

    List<SearchHit> fullTextHits() {
        return hits.stream().filter(hit -> hit.fullTextRank().isPresent()).toList();
    }

    /** A Negative Question passes when none of its vector hits reaches the Minimum Similarity. */
    boolean passed(double minimumSimilarity) {
        return vectorHits().stream().noneMatch(hit -> hit.similarity().getAsDouble() >= minimumSimilarity);
    }
}
