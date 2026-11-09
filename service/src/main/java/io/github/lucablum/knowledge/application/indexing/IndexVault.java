package io.github.lucablum.knowledge.application.indexing;

import org.jmolecules.architecture.hexagonal.PrimaryPort;

/**
 * Reconciliation: brings the index in line with the Collection's Vault, indexing new and changed Notes and removing
 * Notes that are no longer there. A rename is a removal plus a new Note.
 */
@PrimaryPort
public interface IndexVault {

    Reconciled run();
}
