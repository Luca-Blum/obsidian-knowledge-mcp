package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.application.IndexVault;
import io.github.lucablum.knowledge.application.SearchChunks;
import io.github.lucablum.knowledge.domain.SearchHit;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SearchTest {

    @Inject
    TestVault vault;

    @Inject
    IndexVault indexVault;

    @Inject
    SearchChunks search;

    @BeforeEach
    void emptyVault() {
        vault.clear();
    }

    @Test
    void returnsTheChunkClosestToTheQueryAsSearchHit() {
        vault.write("Software-Engineering/Clean Architecture.md", """
                # Clean Architecture

                ## Dependency Rule

                Source code dependencies point only inwards.

                ## Entities

                Entities encapsulate enterprise wide business rules.
                """);
        indexVault.run();

        List<SearchHit> hits = search.search("which way do source code dependencies point", Optional.empty(), 1);

        assertThat(hits).singleElement().satisfies(hit -> {
            assertThat(hit.content()).isEqualTo("Source code dependencies point only inwards.");
            assertThat(hit.notePath()).isEqualTo("Software-Engineering/Clean Architecture.md");
            assertThat(hit.headingPath()).containsExactly("Clean Architecture", "Dependency Rule");
            assertThat(hit.score()).isBetween(0.0, 1.0);
        });
    }

    @Test
    void indexesOnlyMarkdownOutsideFoldersPrefixedWithUnderscoreOrDot() {
        vault.write("Coaching/Kata.md", "Refactoring kata practice");
        vault.write("Coaching/Kata.txt", "Refactoring kata practice");
        vault.write(".obsidian/workspace.md", "Refactoring kata practice");
        vault.write("_inbox/Draft.md", "Refactoring kata practice");
        vault.write("Coaching/_sources/Notes.md", "Refactoring kata practice");
        vault.write("Coaching/Deep/.trash/Old.md", "Refactoring kata practice");
        vault.write("Coaching/Deep/_eval/questions.md", "Refactoring kata practice");
        indexVault.run();

        List<SearchHit> hits = search.search("refactoring kata", Optional.empty(), 10);

        assertThat(hits).extracting(SearchHit::notePath).containsExactly("Coaching/Kata.md");
    }

    @Test
    void notePathsAreVaultRelativeAndNfcNormalized() {
        String decomposed = "U\u0308bungen/Mu\u0308ller.md";
        vault.write(decomposed, "Übungen nach Müller");
        indexVault.run();

        List<SearchHit> hits = search.search("Übungen nach Müller", Optional.empty(), 1);

        assertThat(hits).extracting(SearchHit::notePath).containsExactly("\u00DCbungen/M\u00FCller.md");
    }

    @Test
    void topicFilterKeepsOnlyNotesBelowThatFirstFolder() {
        vault.write("Running/Intervals.md", "Interval training improves speed");
        vault.write("Running/Plans/Marathon.md", "Interval training for the marathon");
        vault.write("Cycling/Intervals.md", "Interval training on the bike");
        vault.write("Index.md", "Interval training overview");
        indexVault.run();

        List<SearchHit> hits = search.search("interval training", Optional.of("Running"), 10);

        assertThat(hits).extracting(SearchHit::notePath)
                .containsExactlyInAnyOrder("Running/Intervals.md", "Running/Plans/Marathon.md");
    }

    @Test
    void rootNotesAreFoundWithoutTopicFilter() {
        vault.write("Running/Intervals.md", "Interval training improves speed");
        vault.write("Index.md", "Interval training overview");
        indexVault.run();

        List<SearchHit> hits = search.search("interval training", Optional.empty(), 10);

        assertThat(hits).extracting(SearchHit::notePath)
                .containsExactlyInAnyOrder("Running/Intervals.md", "Index.md");
    }

    @Test
    void returnsTheKNearestChunksInOrderOfSimilarity() {
        vault.write("Notes.md", """
                ## Far
                apples and pears

                ## Near
                tempo run tempo run

                ## Middle
                tempo run with apples
                """);
        indexVault.run();

        List<SearchHit> hits = search.search("tempo run", Optional.empty(), 2);

        assertThat(hits).extracting(hit -> hit.headingPath().getLast()).containsExactly("Near", "Middle");
        assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());
    }

    @Test
    void notesRemovedFromTheVaultDisappearFromSearch() {
        vault.write("Running/Intervals.md", "Interval training improves speed");
        indexVault.run();
        vault.clear();
        vault.write("Running/Tempo.md", "Tempo runs");
        indexVault.run();

        List<SearchHit> hits = search.search("interval training", Optional.empty(), 10);

        assertThat(hits).extracting(SearchHit::notePath).containsExactly("Running/Tempo.md");
    }
}
