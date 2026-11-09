package io.github.lucablum.knowledge.application.indexing;

import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Indexes every Note of the Collection's Vault and removes Notes that are no longer there. */
@PrimaryPort
public interface IndexVault {

    /** Returns the number of indexed Notes. */
    int run();
}
