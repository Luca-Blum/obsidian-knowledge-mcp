package io.github.lucablum.knowledge.application.search;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * How a Collection searches: {@code k} when the caller leaves it out and its upper bound, the Minimum Similarity a
 * vector candidate must reach, how many Search Hits one Note may take, and how many candidates each of the vector and
 * the full-text search contributes.
 */
@ValueObject
public record SearchSettings(int defaultK, int maxK, double minimumSimilarity, int maxHitsPerNote, int candidates) {

    /** A {@code k} the search would reject itself must never be its default. */
    public SearchSettings {
        if (defaultK < 1 || defaultK > maxK) {
            throw new IllegalArgumentException(
                    "defaultK must be between 1 and maxK (%d), but is %d".formatted(maxK, defaultK));
        }
    }
}
