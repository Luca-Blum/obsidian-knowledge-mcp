package io.github.lucablum.knowledge.application.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.lucablum.knowledge.domain.chunk.ChunkingRules;
import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class FingerprintReIndexTest {

    private static final IndexFingerprint CONFIGURED =
            new IndexFingerprint("bge-m3", 1024, new ChunkingRules(3, 200, 2000, 100));
    private static final Reconciled EVERY_NOTE_INDEXED = new Reconciled(2, 0, 0, List.of());

    private final RecordingBuild build = new RecordingBuild();
    private final ScriptedReconciliation reconciliation = new ScriptedReconciliation();
    private final FingerprintReIndex fingerprintReIndex = new FingerprintReIndex(reconciliation, build, CONFIGURED);
    private final IndexVault indexVault = fingerprintReIndex;
    private final ReIndexCollection reIndex = fingerprintReIndex;

    @Test
    void whenRun_givenIndexBuiltWithConfiguredFingerprint_thenReconcileWithoutStartingOver() {
        build.fingerprint = Optional.of(CONFIGURED);

        Reconciled reconciled = indexVault.run();

        assertThat(reconciled).isEqualTo(EVERY_NOTE_INDEXED);
        assertThat(build.startedOverWith).isEmpty();
        assertThat(reconciliation.runs).isOne();
    }

    @Test
    void whenRun_givenIndexBuiltWithOtherModel_thenStartOverAndIndexEveryNote() {
        build.fingerprint = Optional.of(new IndexFingerprint("nomic-embed-text", 768, CONFIGURED.chunking()));

        Reconciled reconciled = indexVault.run();

        assertThat(reconciled).isEqualTo(EVERY_NOTE_INDEXED);
        assertThat(build.startedOverWith).containsExactly(1024);
        assertThat(reconciliation.runs).isOne();
        assertThat(build.fingerprint).contains(CONFIGURED);
    }

    @Test
    void whenRun_givenOtherChunkingRules_thenStartOver() {
        build.fingerprint = Optional.of(new IndexFingerprint("bge-m3", 1024, new ChunkingRules(2, 200, 2000, 100)));

        indexVault.run();

        assertThat(build.startedOverWith).containsExactly(1024);
    }

    @Test
    void whenRun_givenIndexWithoutFingerprint_thenStartOver() {
        indexVault.run();

        assertThat(build.startedOverWith).containsExactly(1024);
    }

    @Test
    void whenRun_givenEveryNoteFailedInTheReIndex_thenStartOverAgainInsteadOfReconcilingAMixedIndex() {
        reconciliation.result = new Reconciled(0, 0, 0, List.of(new NotePath("Running/Tempo.md")));
        indexVault.run();

        indexVault.run();

        assertThat(build.startedOverWith).containsExactly(1024, 1024);
    }

    @Test
    void whenRun_givenOutdatedIndex_thenTellTheCallerHowFarTheReIndexHasCome() {
        reconciliation.during = progress -> progress.accept(new ReconciliationProgress(1, 2));
        List<ReconciliationProgress> reported = new ArrayList<>();

        indexVault.run(reported::add);

        assertThat(reported).containsExactly(new ReconciliationProgress(1, 2));
    }

    @Test
    void whenReIndex_givenReIndexUnderway_thenRefuseToStartAnotherNamingHowFarTheFirstHasCome() {
        reconciliation.during = progress -> {
            progress.accept(new ReconciliationProgress(1, 2));
            reconciliation.thrownMeanwhile = catchThrowable(() -> reIndex.reIndex(Runnable::run));
        };

        reIndexNow();

        assertThat(reconciliation.thrownMeanwhile).isInstanceOfSatisfying(ReIndexRunning.class,
                refused -> assertThat(refused.progress()).isEqualTo(new ReconciliationProgress(1, 2)));
        assertThat(reconciliation.runs).isOne();
    }

    @Test
    void whenReIndex_givenExecutor_thenRunTheReIndexOnIt() {
        List<Runnable> scheduled = new ArrayList<>();

        CompletableFuture<Reconciled> reIndexed = reIndex.reIndex(scheduled::add);

        assertThat(reconciliation.runs).isZero();
        scheduled.forEach(Runnable::run);
        assertThat(reIndexed).isCompletedWithValue(EVERY_NOTE_INDEXED);
    }

    @Test
    void whenReIndex_givenReconciliationUnderway_thenReportHowManyNotesAreDone() {
        reconciliation.during = progress -> {
            progress.accept(new ReconciliationProgress(0, 2));
            reconciliation.seen.add(reIndex.progress());
            progress.accept(new ReconciliationProgress(1, 2));
            reconciliation.seen.add(reIndex.progress());
        };

        reIndexNow();

        assertThat(reconciliation.seen).containsExactly(
                Optional.of(new ReconciliationProgress(0, 2)), Optional.of(new ReconciliationProgress(1, 2)));
    }

    @Test
    void whenReIndex_givenFinished_thenRecordConfiguredFingerprintAndReportNoProgress() {
        reIndexNow();

        assertThat(build.fingerprint).contains(CONFIGURED);
        assertThat(reIndex.progress()).isEmpty();
    }

    @Test
    void whenReIndex_givenReconciliationFails_thenRecordNoFingerprintAndKeepReportingTheReIndex() {
        build.fingerprint = Optional.of(new IndexFingerprint("nomic-embed-text", 768, CONFIGURED.chunking()));
        reconciliation.during = progress -> {
            throw new IllegalStateException("Vault unreadable");
        };

        assertThatThrownBy(this::reIndexNow).hasRootCauseMessage("Vault unreadable");

        assertThat(build.fingerprint).isEmpty();
        assertThat(reIndex.progress()).contains(ReconciliationProgress.NOT_STARTED);
    }

    @Test
    void whenReIndex_givenStartingOverFails_thenReportNoReIndexOnceTheUntouchedIndexIsCheckedAgain() {
        build.fingerprint = Optional.of(CONFIGURED);
        build.failsToStartOver = true;

        assertThatThrownBy(this::reIndexNow).hasRootCauseMessage("Database unreachable");
        indexVault.run();

        assertThat(build.startedOverWith).isEmpty();
        assertThat(reIndex.progress()).isEmpty();
    }

    @Test
    void whenReIndex_givenEveryNoteFailed_thenRecordNoFingerprintAndKeepReportingTheReIndex() {
        reconciliation.result = new Reconciled(0, 0, 0, List.of(new NotePath("Running/Tempo.md")));

        reIndexNow();

        assertThat(build.fingerprint).isEmpty();
        assertThat(reIndex.progress()).isPresent();
    }

    @Test
    void whenReIndex_givenSomeNotesFailed_thenRecordFingerprintAsTheyHoldNoChunkOfTheOldModel() {
        reconciliation.result = new Reconciled(1, 0, 0, List.of(new NotePath("Running/Tempo.md")));

        reIndexNow();

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

    private Reconciled reIndexNow() {
        return reIndex.reIndex(Runnable::run).join();
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
        private Throwable thrownMeanwhile;
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
