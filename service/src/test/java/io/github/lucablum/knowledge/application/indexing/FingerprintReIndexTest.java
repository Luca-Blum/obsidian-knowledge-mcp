package io.github.lucablum.knowledge.application.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lucablum.knowledge.domain.chunk.ChunkingRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class FingerprintReIndexTest {

    private static final IndexFingerprint CONFIGURED =
            new IndexFingerprint("bge-m3", 1024, new ChunkingRules(3, 200, 2000, 100));
    private static final Reconciled EVERY_NOTE_INDEXED = new Reconciled(2, 0, 0, List.of());

    private final RecordingBuild build = new RecordingBuild();
    private final ScriptedReconciliation reconciliation = new ScriptedReconciliation();
    private final ReIndexCollection reIndex = new FingerprintReIndex(reconciliation, build, CONFIGURED);

    @Test
    void whenReIndexIfOutdated_givenIndexBuiltWithConfiguredFingerprint_thenLeaveIndexAlone() {
        build.fingerprint = Optional.of(CONFIGURED);

        Optional<Reconciled> reIndexed = reIndex.reIndexIfOutdated();

        assertThat(reIndexed).isEmpty();
        assertThat(build.startedOverWith).isEmpty();
        assertThat(reconciliation.runs).isZero();
    }

    @Test
    void whenReIndexIfOutdated_givenIndexBuiltWithOtherModel_thenStartOverAndIndexEveryNote() {
        build.fingerprint = Optional.of(new IndexFingerprint("nomic-embed-text", 768, CONFIGURED.chunking()));

        Optional<Reconciled> reIndexed = reIndex.reIndexIfOutdated();

        assertThat(reIndexed).contains(EVERY_NOTE_INDEXED);
        assertThat(build.startedOverWith).containsExactly(1024);
        assertThat(reconciliation.runs).isOne();
    }

    @Test
    void whenReIndexIfOutdated_givenOtherChunkingRules_thenReIndex() {
        build.fingerprint = Optional.of(new IndexFingerprint("bge-m3", 1024, new ChunkingRules(2, 200, 2000, 100)));

        assertThat(reIndex.reIndexIfOutdated()).contains(EVERY_NOTE_INDEXED);
    }

    @Test
    void whenReIndexIfOutdated_givenIndexWithoutFingerprint_thenReIndex() {
        assertThat(reIndex.reIndexIfOutdated()).contains(EVERY_NOTE_INDEXED);
    }

    @Test
    void whenReIndex_givenReconciliationUnderway_thenReportHowManyNotesAreDone() {
        reconciliation.during = progress -> {
            progress.accept(new Progress(0, 2));
            reconciliation.seen.add(reIndex.progress());
            progress.accept(new Progress(1, 2));
            reconciliation.seen.add(reIndex.progress());
        };

        reIndex.reIndex();

        assertThat(reconciliation.seen).containsExactly(Optional.of(new Progress(0, 2)), Optional.of(new Progress(1, 2)));
    }

    @Test
    void whenReIndex_givenFinished_thenRecordConfiguredFingerprintAndReportNoProgress() {
        reIndex.reIndex();

        assertThat(build.fingerprint).contains(CONFIGURED);
        assertThat(reIndex.progress()).isEmpty();
    }

    @Test
    void whenReIndex_givenReconciliationFails_thenRecordNoFingerprintAndReportNoProgress() {
        build.fingerprint = Optional.of(new IndexFingerprint("nomic-embed-text", 768, CONFIGURED.chunking()));
        reconciliation.during = progress -> {
            throw new IllegalStateException("Vault unreadable");
        };

        assertThatThrownBy(reIndex::reIndex).hasMessage("Vault unreadable");

        assertThat(build.fingerprint).isEmpty();
        assertThat(reIndex.progress()).isEmpty();
    }

    @Test
    void whenProgress_givenNoReIndexRunning_thenReportNone() {
        assertThat(reIndex.progress()).isEmpty();
    }

    /** Forgets its fingerprint when it starts over, as the real index does. */
    private static final class RecordingBuild implements IndexBuild {

        private Optional<IndexFingerprint> fingerprint = Optional.empty();
        private final List<Integer> startedOverWith = new ArrayList<>();

        @Override
        public Optional<IndexFingerprint> fingerprint() {
            return fingerprint;
        }

        @Override
        public void startOver(int dimension) {
            startedOverWith.add(dimension);
            fingerprint = Optional.empty();
        }

        @Override
        public void complete(IndexFingerprint fingerprint) {
            this.fingerprint = Optional.of(fingerprint);
        }
    }

    private static final class ScriptedReconciliation implements IndexVault {

        private int runs;
        private Consumer<Consumer<Progress>> during = progress -> {};
        private final List<Optional<Progress>> seen = new ArrayList<>();

        @Override
        public Reconciled run(Consumer<Progress> progress) {
            runs++;
            during.accept(progress);
            return EVERY_NOTE_INDEXED;
        }
    }
}
