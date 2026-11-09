package io.github.lucablum.knowledge.evaluation;

import java.time.LocalDate;

/** What a report was measured against, so that two reports can be compared. */
record Run(String collection, String embeddingModel, int notes, LocalDate date) {}
