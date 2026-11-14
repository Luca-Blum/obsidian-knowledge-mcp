package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.indexing.IndexVault;
import io.github.lucablum.knowledge.application.indexing.ReIndexCollection;
import io.github.lucablum.knowledge.application.indexing.ReIndexRunning;
import io.github.lucablum.knowledge.application.indexing.ReconciliationProgress;
import io.github.lucablum.knowledge.application.search.SearchChunks;
import io.github.lucablum.knowledge.application.search.SearchRequest;
import io.github.lucablum.knowledge.domain.note.NoteKind;
import io.github.lucablum.knowledge.domain.search.SearchHit;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

    @Inject
    CollectionConfig collection;

    @BeforeEach
    void emptyVault() {
        vault.clear();
    }

    @Test
    void whenSearch_givenQueryMatchingOneChunk_thenReturnClosestChunkAsSearchHit() {
        vault.write("Software-Engineering/Clean Architecture.md", """
                # Clean Architecture

                ## Dependency Rule

                Source code dependencies point only inwards.

                ## Entities

                Entities encapsulate enterprise wide business rules.
                """);
        indexVault.run();

        List<SearchHit> hits = search.search(SearchRequest.of("which way do source code dependencies point").withK(1));

        assertThat(hits).singleElement().satisfies(hit -> {
            assertThat(hit.content()).isEqualTo("Source code dependencies point only inwards.");
            assertThat(hit.notePath()).isEqualTo("Software-Engineering/Clean Architecture.md");
            assertThat(hit.headingPath()).containsExactly("Clean Architecture", "Dependency Rule");
            assertThat(hit.score()).isBetween(-1.0, 1.0);
        });
    }

    @Test
    void whenIndexVault_givenNonMarkdownAndHiddenFolders_thenIndexOnlyMarkdownOutsideThem() {
        vault.write("Coaching/Kata.md", "Refactoring kata practice");
        vault.write("Coaching/Kata.txt", "Refactoring kata practice");
        vault.write(".obsidian/workspace.md", "Refactoring kata practice");
        vault.write("_inbox/Draft.md", "Refactoring kata practice");
        vault.write("Coaching/_sources/Notes.md", "Refactoring kata practice");
        vault.write("Coaching/Deep/.trash/Old.md", "Refactoring kata practice");
        vault.write("Coaching/Deep/_eval/questions.md", "Refactoring kata practice");
        indexVault.run();

        List<SearchHit> hits = search.search(SearchRequest.of("refactoring kata").withK(10));

        assertThat(hits).extracting(SearchHit::notePath).containsExactly("Coaching/Kata.md");
    }

    @Test
    void whenSearch_givenDecomposedFileName_thenReturnVaultRelativeNfcNotePath() {
        String decomposed = "U\u0308bungen/Mu\u0308ller.md";
        vault.write(decomposed, "Übungen nach Müller");
        indexVault.run();

        List<SearchHit> hits = search.search(SearchRequest.of("Übungen nach Müller").withK(1));

        assertThat(hits).extracting(SearchHit::notePath).containsExactly("\u00DCbungen/M\u00FCller.md");
    }

    @Test
    void whenSearch_givenTopic_thenReturnOnlyNotesBelowThatFirstFolder() {
        vault.write("Running/Intervals.md", "Interval training improves speed");
        vault.write("Running/Plans/Marathon.md", "Interval training for the marathon");
        vault.write("Cycling/Intervals.md", "Interval training on the bike");
        vault.write("Index.md", "Interval training overview");
        indexVault.run();

        List<SearchHit> hits = search.search(SearchRequest.of("interval training").inTopic("Running").withK(10));

        assertThat(hits).extracting(SearchHit::notePath)
                .containsExactlyInAnyOrder("Running/Intervals.md", "Running/Plans/Marathon.md");
    }

    @Test
    void whenSearch_givenNoTopic_thenIncludeRootNotes() {
        vault.write("Running/Intervals.md", "Interval training improves speed");
        vault.write("Index.md", "Interval training overview");
        indexVault.run();

        List<SearchHit> hits = search.search(SearchRequest.of("interval training").withK(10));

        assertThat(hits).extracting(SearchHit::notePath)
                .containsExactlyInAnyOrder("Running/Intervals.md", "Index.md");
    }

    @Test
    void whenSearch_givenK_thenReturnKNearestChunksInOrderOfSimilarity() {
        vault.write("Notes.md", """
                ## Far
                apples and pears

                ## Near
                tempo run tempo run

                ## Middle
                tempo run with apples
                """);
        indexVault.run();

        List<SearchHit> hits = search.search(SearchRequest.of("tempo run").withK(2));

        assertThat(hits).extracting(hit -> hit.headingPath().getLast()).containsExactly("Near", "Middle");
        assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());
    }

    @Test
    void whenSearch_givenNoteRemovedFromVault_thenOmitItAfterReindex() {
        vault.write("Running/Intervals.md", "Interval training improves speed");
        indexVault.run();
        vault.clear();
        vault.write("Running/Tempo.md", "Interval training as tempo runs");
        indexVault.run();

        List<SearchHit> hits = search.search(SearchRequest.of("interval training").withK(10));

        assertThat(hits).extracting(SearchHit::notePath).containsExactly("Running/Tempo.md");
    }

    @Test
    void whenSearch_givenQueryFarFromEveryChunk_thenReturnNoSearchHits() {
        vault.write("Running/Intervals.md", "Interval training improves speed");
        indexVault.run();

        List<SearchHit> hits = search.search(SearchRequest.of("apples and pears").withK(10));

        assertThat(hits).isEmpty();
    }

    @Test
    void whenSearch_givenLongNoteMatchingInManyChunks_thenReturnAtMostTwoHitsOfIt() {
        vault.write("Running/Long.md", IntStream.rangeClosed(1, 4)
                .mapToObj(i -> "## Section " + i + "\ntempo run " + i)
                .collect(Collectors.joining("\n\n")));
        vault.write("Running/Short.md", "tempo run once");
        indexVault.run();

        List<SearchHit> hits = search.search(SearchRequest.of("tempo run").withK(10));

        assertThat(hits).extracting(SearchHit::notePath)
                .containsExactlyInAnyOrder("Running/Long.md", "Running/Long.md", "Running/Short.md");
    }

    @Test
    void whenSearch_givenNoteCappedOrCutByK_thenEachHitReportsItsNotesFurtherMatchingChunks() {
        vault.write("Running/Long.md", IntStream.rangeClosed(1, 4)
                .mapToObj(i -> "## Section " + i + "\ntempo run " + i)
                .collect(Collectors.joining("\n\n")));
        vault.write("Running/Short.md", "tempo with apples pears plums cherries");
        vault.write("Cycling/Bike.md", "apples and pears");
        indexVault.run();

        List<SearchHit> all = search.search(SearchRequest.of("tempo run").withK(10));
        List<SearchHit> first = search.search(SearchRequest.of("tempo run").withK(1));

        assertThat(all).extracting(SearchHit::notePath, SearchHit::moreInNote).containsExactlyInAnyOrder(
                tuple("Running/Long.md", 2), tuple("Running/Long.md", 2), tuple("Running/Short.md", 0));
        assertThat(first).extracting(SearchHit::moreInNote).containsExactly(3);
    }

    @Test
    void whenSearch_givenNoK_thenReturnCollectionDefaultNumberOfHits() {
        int defaultK = collection.search().defaultK();
        IntStream.rangeClosed(1, defaultK + 2).forEach(i -> vault.write("Running/Tempo " + i + ".md", "tempo run " + i));
        indexVault.run();

        List<SearchHit> hits = search.search(SearchRequest.of("tempo run"));

        assertThat(hits).hasSize(defaultK);
    }

    @Test
    void whenSearch_givenTypeInFrontmatter_thenOnlyTypeSourceMakesASourceNote() {
        vault.write("Running/Source-Notes/Daniels.md", """
                ---
                type: source
                ---
                Interval training by Daniels
                """);
        vault.write("Running/Idea.md", """
                ---
                type: idea
                ---
                Interval training idea
                """);
        vault.write("Running/Plain.md", "Interval training plain");
        indexVault.run();

        List<SearchHit> hits = search.search(SearchRequest.of("interval training").withK(10));

        assertThat(hits).extracting(SearchHit::notePath, SearchHit::noteKind).containsExactlyInAnyOrder(
                tuple("Running/Source-Notes/Daniels.md", NoteKind.SOURCE),
                tuple("Running/Idea.md", NoteKind.OWN),
                tuple("Running/Plain.md", NoteKind.OWN));
    }

    @Test
    void whenSearch_givenTopicInFrontmatter_thenTopicStillComesFromTheFolder() {
        vault.write("Running/Intervals.md", """
                ---
                topic: Cycling
                ---
                Interval training improves speed
                """);
        indexVault.run();

        List<SearchHit> hits = search.search(SearchRequest.of("interval training").inTopic("Cycling"));

        assertThat(hits).isEmpty();
    }

    @Test
    void whenSearch_givenKeywordsNamingAClassTheQueryMisses_thenReturnTheChunkByFullText() {
        vault.write("Software-Engineering/Index.md", "The class PostgresIndex implements ChunkVectors.");
        indexVault.run();

        List<SearchHit> hits = search.search(
                SearchRequest.of("where are embeddings kept").withKeywords("PostgresIndex").withK(10));

        assertThat(hits).extracting(SearchHit::notePath).containsExactly("Software-Engineering/Index.md");
    }

    @Test
    void whenSearch_givenNoKeywords_thenFullTextSearchesTheQueryStemmedInTheNoteLanguage() {
        vault.write("Running/Training.md", """
                ---
                lang: de
                ---
                Der Läufer trainiert Intervalle
                """);
        indexVault.run();

        List<SearchHit> hits = search.search(SearchRequest.of("laufen").withK(10));

        assertThat(hits).extracting(SearchHit::notePath).containsExactly("Running/Training.md");
    }

    @Test
    void whenSearch_givenKeywordThatIsAGermanStopWord_thenMatchOnlyEnglishChunks() {
        vault.write("Software-Engineering/Architektur.md", """
                ---
                lang: de
                ---
                Die Architektur bleibt stabil
                """);
        vault.write("Software-Engineering/Processes.md", """
                ---
                lang: en
                ---
                Old processes die quietly
                """);
        indexVault.run();

        List<SearchHit> hits = search.search(SearchRequest.of("unrelated").withKeywords("die").withK(10));

        assertThat(hits).extracting(SearchHit::notePath).containsExactly("Software-Engineering/Processes.md");
    }

    @Test
    void whenSearch_givenSeveralKeywords_thenMatchOnlyChunksContainingAll() {
        vault.write("Software-Engineering/Hexagonal.md", "Ports and adapters keep the core clean");
        vault.write("Software-Engineering/Network.md", "Open ports on the firewall");
        indexVault.run();

        List<SearchHit> hits = search.search(SearchRequest.of("unrelated").withKeywords("ports adapters").withK(10));

        assertThat(hits).extracting(SearchHit::notePath).containsExactly("Software-Engineering/Hexagonal.md");
    }

    @Test
    void whenSearch_givenKeywordInOneTitleAndAnotherBody_thenRankTheTitleFirst() {
        vault.write("Software-Engineering/Grid.md", "Tiles laid out in a hexagonal grid");
        vault.write("Software-Engineering/Hexagonal.md", "Ports and adapters keep the core clean");
        indexVault.run();

        List<SearchHit> hits = search.search(SearchRequest.of("unrelated").withKeywords("hexagonal").withK(10));

        assertThat(hits).extracting(SearchHit::notePath)
                .containsExactly("Software-Engineering/Hexagonal.md", "Software-Engineering/Grid.md");
    }

    @Test
    void whenSearch_givenBlankQuery_thenRejectWithIllegalArgument() {
        assertThatThrownBy(() -> search.search(SearchRequest.of("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("query must not be blank");
    }

    @Test
    void whenSearch_givenKAboveMaximum_thenRejectWithIllegalArgument() {
        int maxK = collection.search().maxK();

        assertThatThrownBy(() -> search.search(SearchRequest.of("interval training").withK(maxK + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("k must be between 1 and " + maxK);
    }

    /** Not only the search tool but every caller, such as the evaluation, must never search a mixed index. */
    @Test
    void whenSearch_givenReIndexRunning_thenRefuseNamingHowManyNotesAreDone() {
        QuarkusMock.installMockForType(new RunningReIndex(12, 340), ReIndexCollection.class);

        assertThatThrownBy(() -> search.search(SearchRequest.of("interval training")))
                .isInstanceOfSatisfying(ReIndexRunning.class,
                        refused -> assertThat(refused.progress()).isEqualTo(new ReconciliationProgress(12, 340)));
    }
}
