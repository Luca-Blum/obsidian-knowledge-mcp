package io.github.lucablum.knowledge.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.FakeEmbedding;
import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.indexing.IndexVault;
import io.github.lucablum.knowledge.application.indexing.Vault;
import io.github.lucablum.knowledge.application.indexing.VaultNote;
import io.github.lucablum.knowledge.application.search.SearchChunks;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Measures a Collection's retrieval quality against its Evaluation Set, with the real embedding model served by a
 * local Ollama and a local copy of the Vault. It indexes the Vault from scratch, asks the search use case every
 * question and writes {@code summary.md} and {@code details.md}. The name does not end in {@code Test}, so a normal
 * build never runs it; see the README for how to start it.
 */
@QuarkusTest
@TestProfile(EvaluationRun.RealEmbedding.class)
@EnabledIfSystemProperty(named = "evaluation.set", matches = ".+")
class EvaluationRun {

    @Inject
    Vault vault;

    @Inject
    IndexVault indexVault;

    @Inject
    SearchChunks search;

    @Inject
    CollectionConfig collection;

    @Test
    void whenEvaluate_givenEvaluationSet_thenWriteSummaryAndDetails() throws IOException {
        EvaluationSet set = EvaluationSet.read(Path.of(System.getProperty("evaluation.set")));
        assertThat(set.missingNotes(vault.notes().stream().map(VaultNote::path).toList()))
                .as("Expected Notes that are not in %s", collection.vaultPath())
                .isEmpty();
        int notes = indexVault.run();

        Report report = new Evaluation(search).run(set);

        Run run = new Run(collection.name(), collection.embedding().model(),
                collection.search().minimumSimilarity(), notes, LocalDate.now());
        Path folder = Files.createDirectories(Path.of(System.getProperty("evaluation.report", "target/evaluation")));
        Files.writeString(folder.resolve("summary.md"), report.summary(run));
        Files.writeString(folder.resolve("details.md"), report.details(run));
        System.out.println(report.summary(run));
    }

    /** The {@code evaluation} profile with Ollama instead of the bag-of-words fake every other test uses. */
    public static class RealEmbedding implements QuarkusTestProfile {

        @Override
        public String getConfigProfile() {
            return "evaluation";
        }

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.arc.exclude-types", FakeEmbedding.class.getName());
        }
    }
}
