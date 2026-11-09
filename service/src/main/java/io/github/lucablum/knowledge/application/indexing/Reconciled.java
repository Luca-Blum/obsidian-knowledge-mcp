package io.github.lucablum.knowledge.application.indexing;

import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * What one Reconciliation did: Notes it indexed because they were new or changed, removed, and left as they were,
 * plus the Notes it could not index. Those keep their previous version and are tried again on the next run.
 */
@ValueObject
public record Reconciled(int indexed, int removed, int unchanged, List<NotePath> failed) {

    public Reconciled {
        failed = List.copyOf(failed);
    }
}
