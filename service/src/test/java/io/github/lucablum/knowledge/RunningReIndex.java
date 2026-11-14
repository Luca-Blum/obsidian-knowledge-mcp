package io.github.lucablum.knowledge;

import io.github.lucablum.knowledge.application.indexing.ReIndexCollection;
import io.github.lucablum.knowledge.application.indexing.ReIndexRunning;
import io.github.lucablum.knowledge.application.indexing.Reconciled;
import io.github.lucablum.knowledge.application.indexing.ReconciliationProgress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** A Re-Index that stays at one point of its run, for tests that install it with {@code QuarkusMock}. */
public record RunningReIndex(ReconciliationProgress at) implements ReIndexCollection {

    public RunningReIndex(int done, int total) {
        this(new ReconciliationProgress(done, total));
    }

    @Override
    public CompletableFuture<Reconciled> reIndex(Executor executor) {
        throw new ReIndexRunning(at);
    }

    @Override
    public Optional<ReconciliationProgress> progress() {
        return Optional.of(at);
    }
}
