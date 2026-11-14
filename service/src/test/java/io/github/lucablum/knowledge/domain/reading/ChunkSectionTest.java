package io.github.lucablum.knowledge.domain.reading;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.domain.chunk.Chunk;
import io.github.lucablum.knowledge.domain.chunk.ChunkingRules;
import io.github.lucablum.knowledge.domain.chunk.Splitter;
import io.github.lucablum.knowledge.domain.note.Note;
import io.github.lucablum.knowledge.domain.note.NoteKind;
import io.github.lucablum.knowledge.domain.note.NoteLanguage;
import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The AI Agent reads a Search Hit's section by passing its heading path to read_note. Chunking and reading cut a Note
 * at different headings, so this holds only as long as both agree: whatever a Chunk holds, the sections read under
 * its heading path hold too.
 */
class ChunkSectionTest {

    private static final Note NOTE = new Note(new NotePath("Running/Intervals.md"), NoteKind.OWN, "Intervals",
            Optional.empty(), Optional.empty(), Optional.empty(), new NoteLanguage("en"));

    @Test
    void whenReadChunksHeadingPath_givenHeadingsAboveAndBelowTheHeadingLevel_thenReturnTheChunksContent() {
        assertEveryChunkReadableUnderItsHeadingPath(new ChunkingRules(3, 0, 2000, 0), """
                ---
                title: Intervals
                ---
                Before any heading.

                # Training
                ## Short
                Short intervals build speed.
                ### Recovery
                Jog between them.
                #### Detail
                Two minutes at most.
                ## Long
                Long intervals build endurance.
                """);
    }

    @Test
    void whenReadChunksHeadingPath_givenSmallSectionsMergedUnderTheirCommonPath_thenReturnTheChunksContent() {
        List<Chunk> chunks = assertEveryChunkReadableUnderItsHeadingPath(new ChunkingRules(3, 200, 2000, 0), """
                # Training
                ## Short
                Speed.
                ### Recovery
                Jog.
                ## Long
                Endurance.
                """);

        assertThat(chunks).singleElement()
                .satisfies(merged -> assertThat(merged.headings().segments()).containsExactly("Training"));
    }

    @Test
    void whenReadChunksHeadingPath_givenSectionSplitIntoOverlappingPieces_thenReturnEveryPiece() {
        String paragraphs = String.join("\n\n", List.of(
                "Intervals alternate hard and easy running in fixed blocks of time.",
                "The hard part sits near the speed one could hold for a few minutes.",
                "The easy part lets the heart rate settle before the next repeat.",
                "A session ends with an easy run so the legs recover."));
        assertEveryChunkReadableUnderItsHeadingPath(new ChunkingRules(3, 0, 140, 30),
                "## Short\n" + paragraphs + "\n## Long\nEndurance.\n");
    }

    @Test
    void whenReadChunksHeadingPath_givenHeadingSignsInsideCodeAndRepeatedHeadings_thenReturnTheChunksContent() {
        List<Chunk> chunks = assertEveryChunkReadableUnderItsHeadingPath(new ChunkingRules(3, 0, 2000, 0), """
                ## Plan
                ```
                # not a heading
                ```
                ### Week
                Monday intervals.
                ### Week
                Tuesday tempo.
                ## Log
                ### Week
                Monday done.
                """);

        assertThat(chunks).extracting(chunk -> chunk.headings().segments())
                .containsSubsequence(List.of("Plan", "Week"), List.of("Plan", "Week"));
    }

    /** Returns the Chunks, so a test can make sure it reached the heading paths it is about. */
    private static List<Chunk> assertEveryChunkReadableUnderItsHeadingPath(ChunkingRules rules, String markdown) {
        List<Chunk> chunks = new Splitter(rules).split(NOTE, markdown);
        assertThat(chunks).isNotEmpty();
        for (Chunk chunk : chunks) {
            List<String> sections = new NoteText(markdown).sections(chunk.headings());
            assertThat(sections.stream().map(ChunkSectionTest::words))
                    .as("sections read under %s", chunk.headings())
                    .anySatisfy(section -> assertThat(section).contains(words(chunk.content())));
        }
        return chunks;
    }

    /** The text with every run of whitespace as one space, since a Chunk joins its blocks and headings anew. */
    private static String words(String text) {
        return text.strip().replaceAll("\\s+", " ");
    }
}
