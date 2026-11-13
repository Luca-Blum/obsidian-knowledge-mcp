package io.github.lucablum.knowledge.application.indexing;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.Application;

/**
 * Compares the fingerprint the index was built with against the configured one, and on a difference starts the index
 * over and lets Reconciliation index every Note again. The fingerprint is recorded only once that run is through, so
 * a Re-Index cut short by a crash starts again on the next start-up.
 */
@Application
public class FingerprintReIndex implements ReIndexCollection {

    private static final Logger LOG = System.getLogger(FingerprintReIndex.class.getName());

    private final IndexVault reconciliation;
    private final IndexBuild build;
    private final IndexFingerprint configured;
    private volatile Progress progress;

    public FingerprintReIndex(IndexVault reconciliation, IndexBuild build, IndexFingerprint configured) {
        this.reconciliation = reconciliation;
        this.build = build;
        this.configured = configured;
    }

    @Override
    public synchronized Optional<Reconciled> reIndexIfOutdated() {
        Optional<IndexFingerprint> built = build.fingerprint();
        if (built.equals(Optional.of(configured))) {
            return Optional.empty();
        }
        LOG.log(Level.INFO, "The index was built with {0}, but {1} is configured; starting a Re-Index",
                built.map(IndexFingerprint::toString).orElse("an unknown fingerprint"), configured);
        return Optional.of(reIndex());
    }

    /** Notes that fail keep the mark of a changed Note, so a later Reconciliation indexes them with the new model. */
    @Override
    public synchronized Reconciled reIndex() {
        progress = new Progress(0, 0);
        try {
            build.startOver(configured.dimension());
            Reconciled reconciled = reconciliation.run(done -> progress = done);
            build.complete(configured);
            return reconciled;
        } finally {
            progress = null;
        }
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.ofNullable(progress);
    }
}
