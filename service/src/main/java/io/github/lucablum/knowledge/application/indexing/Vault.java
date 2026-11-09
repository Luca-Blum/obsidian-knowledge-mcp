package io.github.lucablum.knowledge.application.indexing;

import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Reads the Collection's Vault. Used on the indexing side only. */
@SecondaryPort
public interface Vault {

    /** Every Note in the Vault, after the ignore rules are applied. */
    List<VaultNote> notes();
}
