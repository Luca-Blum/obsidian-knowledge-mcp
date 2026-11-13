package io.github.lucablum.knowledge;

import io.github.lucablum.knowledge.application.indexing.Progress;
import io.github.lucablum.knowledge.application.indexing.ReIndexCollection;
import io.github.lucablum.knowledge.application.indexing.Reconciled;
import java.util.Optional;

/** A Re-Index that stays at one point of its run, for tests that install it with {@code QuarkusMock}. */
public record RunningReIndex(Progress at) implements ReIndexCollection {

    @Override
    public Optional<Reconciled> reIndexIfOutdated() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reconciled reIndex() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(at);
    }
}
