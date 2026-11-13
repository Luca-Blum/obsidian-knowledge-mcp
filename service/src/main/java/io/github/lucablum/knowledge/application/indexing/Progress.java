package io.github.lucablum.knowledge.application.indexing;

import org.jmolecules.ddd.annotation.ValueObject;

/** How far a run through the Vault has come: {@code done} of its {@code total} Notes. */
@ValueObject
public record Progress(int done, int total) {

    /** As the AI Agent and the health check read it, e.g. "12 of 340 Notes". */
    public String describe() {
        return "%d of %d Notes".formatted(done, total);
    }
}
