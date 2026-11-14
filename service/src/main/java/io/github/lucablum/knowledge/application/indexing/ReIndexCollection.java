package io.github.lucablum.knowledge.application.indexing;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/**
 * Re-Index: rebuilds the whole index from the Vault, so a changed embedding model or changed chunking rules never
 * leave a mixed index behind. Reconciliation starts one by itself when the fingerprint differs. While it runs, the
 * Collection answers no searches.
 */
@PrimaryPort
public interface ReIndexCollection {

    /**
     * Starts the index over on {@code executor} and indexes every Note, whatever fingerprint the index was built with.
     * The refusal comes at once, so the caller learns it before the Re-Index would have begun.
     *
     * @throws ReIndexRunning if a Re-Index already runs or waits for its turn
     */
    CompletableFuture<Reconciled> reIndex(Executor executor);

    /**
     * How far the Re-Index has come, or empty if the index was built with the configured fingerprint. A Re-Index that
     * did not start yet or failed and waits to be tried again reports {@link ReconciliationProgress#NOT_STARTED}.
     */
    Optional<ReconciliationProgress> progress();
}
