package io.github.lucablum.knowledge.application;

import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Indexes every Note of the Collection's Vault and removes Notes that are no longer there. */
@PrimaryPort
public interface IndexVault {

    /** Returns the number of indexed Notes. */
    int run();
}
