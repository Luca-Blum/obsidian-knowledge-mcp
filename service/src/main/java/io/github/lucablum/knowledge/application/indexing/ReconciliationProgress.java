package io.github.lucablum.knowledge.application.indexing;

import org.jmolecules.ddd.annotation.ValueObject;

/** How far a run through the Vault has come: {@code done} of its {@code total} Notes. */
@ValueObject
public record ReconciliationProgress(int done, int total) {

    /** A run that has not counted the Notes of the Vault yet. */
    public static final ReconciliationProgress NOT_STARTED = new ReconciliationProgress(0, 0);

    /** As the AI Agent and the health check read it, e.g. "12 of 340 Notes". */
    public String describe() {
        return equals(NOT_STARTED) ? "not started yet" : "%d of %d Notes".formatted(done, total);
    }
}
