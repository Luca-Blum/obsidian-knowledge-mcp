package io.github.lucablum.knowledge.application.indexing;

import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/**
 * Re-Index: rebuilds the whole index from the Vault, so a changed embedding model or changed chunking rules never
 * leave a mixed index behind. While it runs, the Collection answers no searches.
 */
@PrimaryPort
public interface ReIndexCollection {

    /** Re-indexes unless the index was built with the configured fingerprint; empty if it was. */
    Optional<Reconciled> reIndexIfOutdated();

    Reconciled reIndex();

    /** How far the running Re-Index has come, or empty if none runs. */
    Optional<Progress> progress();
}
