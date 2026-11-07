package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.lucablum.knowledge.application.BrowseCollection;
import io.github.lucablum.knowledge.application.IndexVault;
import io.github.lucablum.knowledge.domain.SourceNoteListing;
import io.github.lucablum.knowledge.domain.Topic;
import io.github.lucablum.knowledge.domain.TopicCount;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BrowseTest {

    @Inject
    TestVault vault;

    @Inject
    IndexVault indexVault;

    @Inject
    BrowseCollection browse;

    @BeforeEach
    void emptyVault() {
        vault.clear();
    }

    @Test
    void whenListTopics_givenNotesInFoldersAndRoot_thenReturnEachTopicWithItsNoteCount() {
        vault.write("Running/Intervals.md", "Interval training");
        vault.write("Running/Plans/Marathon.md", "Marathon plan");
        vault.write("Running/_sources/Hidden.md", "Not a Note");
        vault.write("Cycling/Intervals.md", "Intervals on the bike");
        vault.write("Index.md", "Overview");
        indexVault.run();

        assertThat(browse.topics()).containsExactly(
                new TopicCount(new Topic("Cycling"), 1),
                new TopicCount(new Topic("Running"), 2));
    }

    @Test
    void whenListSources_givenSourceAndOwnNotes_thenReturnOnlySourceNotesOfThatTopic() {
        vault.write("Running/Source-Notes/Daniels.md", source("title: Daniels' Running Formula"));
        vault.write("Running/Own.md", "Own thoughts on intervals");
        vault.write("Running/Plans/Seiler.md", source("title: Seiler"));
        vault.write("Cycling/Source-Notes/Coggan.md", source("title: Coggan"));
        indexVault.run();

        assertThat(browse.sourceNotes(new Topic("Running"))).extracting(SourceNoteListing::notePath)
                .containsExactlyInAnyOrder("Running/Source-Notes/Daniels.md", "Running/Plans/Seiler.md");
    }

    @Test
    void whenListSources_givenNoTitleInFrontmatter_thenTitleIsFileNameNotFirstHeading() {
        vault.write("Running/Source-Notes/Daniels Formula.md", """
                ---
                type: source
                ---
                # The Running Formula
                text
                """);
        indexVault.run();

        assertThat(browse.sourceNotes(new Topic("Running"))).extracting(SourceNoteListing::title)
                .containsExactly("Daniels Formula");
    }

    @Test
    void whenListSources_givenPublishedOfEveryPrecision_thenDateIsPublishedAsWritten() {
        vault.write("Running/Year.md", source("title: Year\npublished: 2021\nimported: 2026-01-05"));
        vault.write("Running/Month.md", source("title: Month\npublished: 2021-03\nimported: 2026-01-05"));
        vault.write("Running/Day.md", source("title: Day\npublished: 2021-03-14\nimported: 2026-01-05"));
        indexVault.run();

        assertThat(browse.sourceNotes(new Topic("Running"))).extracting(SourceNoteListing::title, SourceNoteListing::date)
                .containsExactlyInAnyOrder(
                        tuple("Year", Optional.of("2021")),
                        tuple("Month", Optional.of("2021-03")),
                        tuple("Day", Optional.of("2021-03-14")));
    }

    @Test
    void whenListSources_givenOnlyImported_thenDateIsImportedDate() {
        vault.write("Running/Daniels.md", source("imported: 2026-01-05"));
        indexVault.run();

        assertThat(browse.sourceNotes(new Topic("Running"))).extracting(SourceNoteListing::date)
                .containsExactly(Optional.of("2026-01-05"));
    }

    @Test
    void whenListSources_givenNoOrInvalidDates_thenDateStaysEmpty() {
        vault.write("Running/None.md", source("title: None"));
        vault.write("Running/Invalid.md", source("title: Invalid\npublished: spring 2021\nimported: yesterday"));
        indexVault.run();

        assertThat(browse.sourceNotes(new Topic("Running"))).extracting(SourceNoteListing::date)
                .containsExactly(Optional.empty(), Optional.empty());
    }

    @Test
    void whenListSources_givenAuthor_thenListingCarriesIt() {
        vault.write("Running/Daniels.md", source("author: Jack Daniels"));
        vault.write("Running/Anonymous.md", source("title: Anonymous"));
        indexVault.run();

        assertThat(browse.sourceNotes(new Topic("Running"))).extracting(SourceNoteListing::notePath, SourceNoteListing::author)
                .containsExactlyInAnyOrder(
                        tuple("Running/Daniels.md", Optional.of("Jack Daniels")),
                        tuple("Running/Anonymous.md", Optional.empty()));
    }

    private static String source(String frontmatter) {
        return "---\ntype: source\n" + frontmatter + "\n---\nSource text\n";
    }
}
