package io.github.lucablum.knowledge.application.indexing;

/** The Collection refuses a search or a second Re-Index, because a Re-Index runs or waits for its turn. */
public class ReIndexRunning extends RuntimeException {

    private final ReconciliationProgress progress;

    public ReIndexRunning(ReconciliationProgress progress) {
        super("A Re-Index is running (progress: %s)".formatted(progress.describe()));
        this.progress = progress;
    }

    /** How far the running Re-Index has come. */
    public ReconciliationProgress progress() {
        return progress;
    }
}
