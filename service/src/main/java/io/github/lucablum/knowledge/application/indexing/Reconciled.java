package io.github.lucablum.knowledge.application.indexing;

import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.List;
import java.util.stream.Stream;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * What one Reconciliation did: Notes it indexed because they were new or changed, removed, and left as they were,
 * plus the Notes it could not index. Those keep their previous version and are tried again on the next run. What
 * happened to each Note adds up to what happened to the Vault.
 */
@ValueObject
public record Reconciled(int indexed, int removed, int unchanged, List<NotePath> failed) {

    public static final Reconciled NOTHING = new Reconciled(0, 0, 0, List.of());

    public Reconciled {
        failed = List.copyOf(failed);
    }

    public static Reconciled ofIndexedNote() {
        return new Reconciled(1, 0, 0, List.of());
    }

    public static Reconciled ofUnchangedNote() {
        return new Reconciled(0, 0, 1, List.of());
    }

    public static Reconciled ofFailedNote(NotePath note) {
        return new Reconciled(0, 0, 0, List.of(note));
    }

    public static Reconciled ofRemovedNotes(int removed) {
        return new Reconciled(0, removed, 0, List.of());
    }

    public Reconciled plus(Reconciled other) {
        return new Reconciled(
                indexed + other.indexed,
                removed + other.removed,
                unchanged + other.unchanged,
                Stream.concat(failed.stream(), other.failed.stream()).toList());
    }
}
