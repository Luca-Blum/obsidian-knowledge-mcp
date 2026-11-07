package io.github.lucablum.knowledge.application;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * How a Collection searches: {@code k} when the caller leaves it out and its upper bound, the Minimum Similarity a
 * vector candidate must reach, how many Search Hits one Note may take, and how many vector candidates are looked at.
 */
@ValueObject
public record SearchSettings(int defaultK, int maxK, double minimumSimilarity, int maxHitsPerNote, int candidates) {
}
