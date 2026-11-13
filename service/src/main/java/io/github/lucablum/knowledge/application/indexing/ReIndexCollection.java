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

    /** Starts the index over and indexes every Note, whatever fingerprint the index was built with. */
    Reconciled reIndex();

    /**
     * How far the Re-Index has come, or empty if the index was built with the configured fingerprint. A Re-Index that
     * did not start yet or failed and waits to be tried again reports {@link ReconciliationProgress#NOT_STARTED}.
     */
    Optional<ReconciliationProgress> progress();

    /** Whether a Re-Index run is under way right now. */
    boolean running();
}
