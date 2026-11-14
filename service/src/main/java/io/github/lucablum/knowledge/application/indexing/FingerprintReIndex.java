package io.github.lucablum.knowledge.application.indexing;

import java.lang.System.Logger.Level;
import java.lang.System.Logger;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.jmolecules.architecture.hexagonal.Application;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Keeps Reconciliation and the Re-Index from ever mixing two fingerprints in one index. Reconciliation compares the
 * fingerprint the index was built with against the configured one, and on a difference starts the index over and lets
 * the content-hash Reconciliation index every Note again instead. The fingerprint is recorded only once that run is
 * through, so a Re-Index cut short by a crash starts again with the next Reconciliation. Until the fingerprints match,
 * the index counts as being re-indexed, whether a run is under way, waits for its turn or failed and waits to be tried
 * again. Only one Reconciliation or Re-Index runs at a time.
 */
@Application
public class FingerprintReIndex implements IndexVault, ReIndexCollection {

    private static final Logger LOG = System.getLogger(FingerprintReIndex.class.getName());

    /** Whether the index was built with the configured fingerprint, as far as this instance knows. */
    @ValueObject
    private enum State { UNCHECKED, UP_TO_DATE, OUTDATED }

    private final IndexVault reconciliation;
    private final IndexBuild build;
    private final IndexFingerprint configured;
    private final ReentrantLock oneRunAtATime = new ReentrantLock();
    private final AtomicReference<State> state = new AtomicReference<>(State.UNCHECKED);
    /** Whether a Re-Index runs or waits for its turn; whoever sets it runs the Re-Index. */
    private final AtomicBoolean reIndexUnderway = new AtomicBoolean();
    private volatile ReconciliationProgress progress = ReconciliationProgress.NOT_STARTED;

    public FingerprintReIndex(IndexVault reconciliation, IndexBuild build, IndexFingerprint configured) {
        this.reconciliation = reconciliation;
        this.build = build;
        this.configured = configured;
    }

    /** Reconciles an index built with the configured fingerprint and re-indexes any other. */
    @Override
    public Reconciled run(Consumer<ReconciliationProgress> progress) {
        oneRunAtATime.lock();
        try {
            Optional<IndexFingerprint> built = build.fingerprint();
            if (built.equals(Optional.of(configured))) {
                state.set(State.UP_TO_DATE);
                return reconciliation.run(progress);
            }
            if (!reIndexUnderway.compareAndSet(false, true)) {
                LOG.log(Level.INFO, "The index is outdated and a Re-Index waits for its turn; leaving it to that");
                return Reconciled.NOTHING;
            }
            LOG.log(Level.INFO, "The index was built with {0}, but {1} is configured; starting a Re-Index",
                    built.map(IndexFingerprint::toString).orElse("an unknown fingerprint"), configured);
            return reIndexEveryNote(progress);
        } finally {
            oneRunAtATime.unlock();
        }
    }

    @Override
    public CompletableFuture<Reconciled> reIndex(Executor executor) {
        if (!reIndexUnderway.compareAndSet(false, true)) {
            throw new ReIndexRunning(progress);
        }
        try {
            return CompletableFuture.supplyAsync(this::reIndexWhenItsTurnComes, executor);
        } catch (RuntimeException e) {
            reIndexUnderway.set(false);
            throw e;
        }
    }

    /** Until a Re-Index or its check ran, the first question compares the fingerprints. */
    @Override
    public Optional<ReconciliationProgress> progress() {
        if (state.get() == State.UNCHECKED) {
            state.compareAndSet(State.UNCHECKED, comparedWithConfigured());
        }
        return state.get() == State.UP_TO_DATE ? Optional.empty() : Optional.of(progress);
    }

    private Reconciled reIndexWhenItsTurnComes() {
        oneRunAtATime.lock();
        try {
            return reIndexEveryNote(done -> {});
        } finally {
            oneRunAtATime.unlock();
        }
    }

    /**
     * Runs the Re-Index its caller set {@link #reIndexUnderway} for. Notes that fail keep the mark of a changed Note
     * and hold no Chunk, so a later Reconciliation indexes them with the new model. Only a run in which every Note
     * failed, as when the model cannot be reached, is not complete.
     */
    private Reconciled reIndexEveryNote(Consumer<ReconciliationProgress> listener) {
        state.set(State.OUTDATED);
        try {
            build.startOver(configured.dimension());
            Reconciled reconciled = reconciliation.run(done -> {
                progress = done;
                listener.accept(done);
            });
            if (indexedAnything(reconciled)) {
                build.complete(configured);
                state.set(State.UP_TO_DATE);
            } else {
                LOG.log(Level.WARNING, "No Note could be indexed with {0}; the index stays outdated", configured);
            }
            return reconciled;
        } finally {
            reIndexUnderway.set(false);
            progress = ReconciliationProgress.NOT_STARTED;
        }
    }

    private State comparedWithConfigured() {
        return build.fingerprint().equals(Optional.of(configured)) ? State.UP_TO_DATE : State.OUTDATED;
    }

    private static boolean indexedAnything(Reconciled reconciled) {
        return reconciled.failed().isEmpty() || reconciled.indexed() > 0;
    }
}
