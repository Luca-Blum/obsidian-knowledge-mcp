package io.github.lucablum.knowledge.application.indexing;

import java.util.function.Consumer;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/**
 * Reconciliation: brings the index in line with the Collection's Vault, indexing new and changed Notes and removing
 * Notes that are no longer there. A rename is a removal plus a new Note.
 */
@PrimaryPort
public interface IndexVault {

    default Reconciled run() {
        return run(progress -> {});
    }

    /** Tells {@code progress} how many Notes are done before the first and after every Note. */
    Reconciled run(Consumer<Progress> progress);
}
