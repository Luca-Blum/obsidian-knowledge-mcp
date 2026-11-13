package io.github.lucablum.knowledge.evaluation;

import java.time.LocalDate;

/** What a report was measured against, so that two reports can be compared. */
record Run(String collection, String embeddingModel, double minimumSimilarity, Fusion fusion, int notes,
        LocalDate date) {

    /** How the vector and the full-text ranking were fused; a full-text weight of 0 is plain vector search. */
    record Fusion(int k, double vectorWeight, double fullTextWeight) {}
}
