package io.github.lucablum.knowledge.application.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.lucablum.knowledge.domain.chunk.ChunkingRules;
import io.github.lucablum.knowledge.domain.note.NotePath;
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
            progress.accept(new ReconciliationProgress(0, 2));
            reconciliation.seen.add(reIndex.progress());
            progress.accept(new ReconciliationProgress(1, 2));
            reconciliation.seen.add(reIndex.progress());
            reconciliation.runningMeanwhile = reIndex.running();
        };

        reIndex.reIndex();

        assertThat(reconciliation.runningMeanwhile).isTrue();
        assertThat(reIndex.running()).isFalse();
        assertThat(reconciliation.seen).containsExactly(
                Optional.of(new ReconciliationProgress(0, 2)), Optional.of(new ReconciliationProgress(1, 2)));
    }

    @Test
    void whenReIndex_givenFinished_thenRecordConfiguredFingerprintAndReportNoProgress() {
        reIndex.reIndex();

        assertThat(build.fingerprint).contains(CONFIGURED);
        assertThat(reIndex.progress()).isEmpty();
    }

    @Test
    void whenReIndex_givenReconciliationFails_thenRecordNoFingerprintAndKeepReportingTheReIndex() {
        build.fingerprint = Optional.of(new IndexFingerprint("nomic-embed-text", 768, CONFIGURED.chunking()));
        reconciliation.during = progress -> {
            throw new IllegalStateException("Vault unreadable");
        };

        assertThatThrownBy(reIndex::reIndex).hasMessage("Vault unreadable");

        assertThat(build.fingerprint).isEmpty();
        assertThat(reIndex.progress()).contains(ReconciliationProgress.NOT_STARTED);
    }

    @Test
    void whenReIndex_givenStartingOverFails_thenReportNoReIndexOnceTheUntouchedIndexIsCheckedAgain() {
        build.fingerprint = Optional.of(CONFIGURED);
        build.failsToStartOver = true;

        assertThatThrownBy(reIndex::reIndex).hasMessage("Database unreachable");

        assertThat(reIndex.reIndexIfOutdated()).isEmpty();
        assertThat(reIndex.progress()).isEmpty();
    }

    @Test
    void whenReIndex_givenEveryNoteFailed_thenRecordNoFingerprintAndKeepReportingTheReIndex() {
        reconciliation.result = new Reconciled(0, 0, 0, List.of(new NotePath("Running/Tempo.md")));

        reIndex.reIndex();

        assertThat(build.fingerprint).isEmpty();
        assertThat(reIndex.progress()).isPresent();
    }

    @Test
    void whenReIndex_givenSomeNotesFailed_thenRecordFingerprintAsTheyHoldNoChunkOfTheOldModel() {
        reconciliation.result = new Reconciled(1, 0, 0, List.of(new NotePath("Running/Tempo.md")));

        reIndex.reIndex();

        assertThat(build.fingerprint).contains(CONFIGURED);
        assertThat(reIndex.progress()).isEmpty();
    }

    @Test
    void whenProgress_givenIndexBuiltWithConfiguredFingerprint_thenReportNone() {
        build.fingerprint = Optional.of(CONFIGURED);

        assertThat(reIndex.progress()).isEmpty();
    }

    @Test
    void whenProgress_givenIndexBuiltWithOtherFingerprintBeforeAnyReIndex_thenReportOneNotStartedYet() {
        build.fingerprint = Optional.of(new IndexFingerprint("nomic-embed-text", 768, CONFIGURED.chunking()));

        assertThat(reIndex.progress()).contains(ReconciliationProgress.NOT_STARTED);
    }

    /** Forgets its fingerprint when it starts over, as the real index does. */
    private static final class RecordingBuild implements IndexBuild {

        private Optional<IndexFingerprint> fingerprint = Optional.empty();
        private final List<Integer> startedOverWith = new ArrayList<>();
        private boolean failsToStartOver;

        @Override
        public Optional<IndexFingerprint> fingerprint() {
            return fingerprint;
        }

        @Override
        public void startOver(int dimension) {
            if (failsToStartOver) {
                throw new IllegalStateException("Database unreachable");
            }
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
        private Reconciled result = EVERY_NOTE_INDEXED;
        private boolean runningMeanwhile;
        private Consumer<Consumer<ReconciliationProgress>> during = progress -> {};
        private final List<Optional<ReconciliationProgress>> seen = new ArrayList<>();

        @Override
        public Reconciled run(Consumer<ReconciliationProgress> progress) {
            runs++;
            during.accept(progress);
            return result;
        }
    }
}
