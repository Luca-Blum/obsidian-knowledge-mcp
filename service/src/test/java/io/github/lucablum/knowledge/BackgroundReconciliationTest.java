package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.application.indexing.IndexBuild;
import io.github.lucablum.knowledge.application.indexing.IndexFingerprint;
import io.github.lucablum.knowledge.application.search.SearchChunks;
import io.github.lucablum.knowledge.application.search.SearchRequest;
import io.github.lucablum.knowledge.domain.search.SearchHit;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Reconciliation runs on its own: once at start-up, and within seconds of a file change in the Vault. Before it, at
 * start-up, an index built with another fingerprint is re-indexed.
 */
@QuarkusTest
@TestProfile(BackgroundReconciliationTest.Enabled.class)
class BackgroundReconciliationTest {

    /** Far longer than any test waits, so only start-up and the file watcher can trigger a Reconciliation. */
    private static final String INTERVAL = "1h";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    /** No other test configures it, so whatever an earlier test left in the index has another fingerprint. */
    private static final int OVERLAP = 7;

    @Inject
    TestVault vault;

    @Inject
    SearchChunks search;

    @Inject
    IndexBuild build;

    @Test
    void whenServiceStarts_givenNoteWrittenWhileStopped_thenIndexIt() {
        assertThat(notePathsFoundWithin(TIMEOUT, "Running/Tempo.md", "threshold pace"))
                .contains("Running/Tempo.md");
    }

    @Test
    void whenNoteWritten_givenServiceRunning_thenIndexItWithinSeconds() {
        awaitStartupReconciliation();

        vault.write("Cycling/Indoor/Sweet Spot.md", "Sweet spot intervals on the trainer");

        assertThat(notePathsFoundWithin(TIMEOUT, "Cycling/Indoor/Sweet Spot.md", "sweet spot trainer"))
                .contains("Cycling/Indoor/Sweet Spot.md");
    }

    @Test
    void whenServiceStarts_givenIndexBuiltWithOtherChunkingRules_thenReIndexAndRecordTheConfiguredOnes() {
        assertThat(fingerprintWithin(TIMEOUT)).hasValueSatisfying(
                fingerprint -> assertThat(fingerprint.chunking().overlapCharacters()).isEqualTo(OVERLAP));
    }

    /** Polls until the index has a fingerprint, or {@code timeout} has passed. */
    private Optional<IndexFingerprint> fingerprintWithin(Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (build.fingerprint().isEmpty() && Instant.now().isBefore(deadline)) {
            pause();
        }
        return build.fingerprint();
    }

    private void awaitStartupReconciliation() {
        notePathsFoundWithin(TIMEOUT, "Running/Tempo.md", "threshold pace");
    }

    /** Searches until {@code expected} is among the Note paths found, or {@code timeout} has passed. */
    private List<String> notePathsFoundWithin(Duration timeout, String expected, String query) {
        Instant deadline = Instant.now().plus(timeout);
        List<String> found = notePaths(query);
        while (!found.contains(expected) && Instant.now().isBefore(deadline)) {
            pause();
            found = notePaths(query);
        }
        return found;
    }

    private List<String> notePaths(String query) {
        return search.search(SearchRequest.of(query).withK(10)).stream().map(SearchHit::notePath).toList();
    }

    private static void pause() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** A Vault of its own that already holds a Note when the service starts, as after a stop. */
    public static class Enabled implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            try {
                Path vault = Files.createTempDirectory("background-reconciliation-vault");
                Files.createDirectories(vault.resolve("Running"));
                Files.writeString(vault.resolve("Running/Tempo.md"), "Tempo runs at threshold pace");
                return Map.of(
                        "collection.vault-path", vault.toString(),
                        "collection.reconciliation.enabled", "true",
                        "collection.reconciliation.interval", INTERVAL,
                        "collection.reconciliation.quiet-period", "200ms",
                        "collection.chunking.overlap-characters", String.valueOf(OVERLAP));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
