package io.github.lucablum.knowledge.application.indexing;

import java.lang.System.Logger.Level;
import java.lang.System.Logger;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jmolecules.architecture.hexagonal.Application;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Compares the fingerprint the index was built with against the configured one, and on a difference starts the index
 * over and lets Reconciliation index every Note again. The fingerprint is recorded only once that run is through, so
 * a Re-Index cut short by a crash starts again on the next start-up. Until the fingerprints match, the index counts
 * as being re-indexed, whether a run is under way, waits for its turn or failed and waits to be tried again.
 */
@Application
public class FingerprintReIndex implements ReIndexCollection {

    private static final Logger LOG = System.getLogger(FingerprintReIndex.class.getName());

    /** Whether the index was built with the configured fingerprint, as far as this instance knows. */
    @ValueObject
    private enum State { UNCHECKED, UP_TO_DATE, OUTDATED }

    private final IndexVault reconciliation;
    private final IndexBuild build;
    private final IndexFingerprint configured;
    private final AtomicReference<State> state = new AtomicReference<>(State.UNCHECKED);
    private volatile ReconciliationProgress progress = ReconciliationProgress.NOT_STARTED;
    private volatile boolean running;

    public FingerprintReIndex(IndexVault reconciliation, IndexBuild build, IndexFingerprint configured) {
        this.reconciliation = reconciliation;
        this.build = build;
        this.configured = configured;
    }

    @Override
    public synchronized Optional<Reconciled> reIndexIfOutdated() {
        Optional<IndexFingerprint> built = build.fingerprint();
        if (built.equals(Optional.of(configured))) {
            state.set(State.UP_TO_DATE);
            return Optional.empty();
        }
        LOG.log(Level.INFO, "The index was built with {0}, but {1} is configured; starting a Re-Index",
                built.map(IndexFingerprint::toString).orElse("an unknown fingerprint"), configured);
        return Optional.of(reIndex());
    }

    /**
     * Notes that fail keep the mark of a changed Note and hold no Chunk, so a later Reconciliation indexes them with
     * the new model. Only a run in which every Note failed, as when the model cannot be reached, is not complete.
     */
    @Override
    public synchronized Reconciled reIndex() {
        state.set(State.OUTDATED);
        running = true;
        try {
            build.startOver(configured.dimension());
            Reconciled reconciled = reconciliation.run(done -> progress = done);
            if (indexedAnything(reconciled)) {
                build.complete(configured);
                state.set(State.UP_TO_DATE);
            }
            return reconciled;
        } finally {
            running = false;
            progress = ReconciliationProgress.NOT_STARTED;
        }
    }

    @Override
    public boolean running() {
        return running;
    }

    /** Until a Re-Index or its check ran, the first question compares the fingerprints. */
    @Override
    public Optional<ReconciliationProgress> progress() {
        if (state.get() == State.UNCHECKED) {
            state.compareAndSet(State.UNCHECKED, comparedWithConfigured());
        }
        return state.get() == State.UP_TO_DATE ? Optional.empty() : Optional.of(progress);
    }

    private State comparedWithConfigured() {
        return build.fingerprint().equals(Optional.of(configured)) ? State.UP_TO_DATE : State.OUTDATED;
    }

    private static boolean indexedAnything(Reconciled reconciled) {
        return reconciled.failed().isEmpty() || reconciled.indexed() > 0;
    }
}
