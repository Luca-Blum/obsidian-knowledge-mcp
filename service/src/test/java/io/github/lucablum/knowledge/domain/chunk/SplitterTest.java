package io.github.lucablum.knowledge.domain.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.github.lucablum.knowledge.domain.note.NotePath;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SplitterTest {

    private static final NotePath NOTE = new NotePath("Running/Intervals.md");

    private final Splitter splitter = new Splitter();

    @Test
    void whenSplit_givenNestedHeadings_thenReturnOneChunkPerSectionWithItsHeadingPath() {
        List<Chunk> chunks = splitter.split(NOTE, """
                # A
                intro
                ## B
                body b
                ## C
                body c
                """);

        assertThat(chunks).extracting(Chunk::ordinal).containsExactly(0, 1, 2);
        assertThat(chunks).extracting(Chunk::content).containsExactly("intro", "body b", "body c");
        assertThat(chunks).extracting(chunk -> chunk.headings().segments())
                .containsExactly(List.of("A"), List.of("A", "B"), List.of("A", "C"));
    }

    @Test
    void whenSplit_givenShallowerHeadingAfterDeeperOne_thenDropTheDeeperHeadings() {
        List<Chunk> chunks = splitter.split(NOTE, """
                # A
                ## B
                x
                # C
                y
                """);

        assertThat(chunks).extracting(chunk -> chunk.headings().segments())
                .containsExactly(List.of("A", "B"), List.of("C"));
    }

    @Test
    void whenSplit_givenTextBeforeFirstHeading_thenItsHeadingPathIsEmpty() {
        List<Chunk> chunks = splitter.split(NOTE, """
                intro
                # A
                text
                """);

        assertThat(chunks).extracting(chunk -> chunk.headings().segments())
                .containsExactly(List.of(), List.of("A"));
    }

    @Test
    void whenSplit_givenHeadingWithoutContent_thenSkipItsSection() {
        List<Chunk> chunks = splitter.split(NOTE, """
                # A
                ## B
                text
                """);

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.ordinal()).isZero();
            assertThat(chunk.headings().segments()).containsExactly("A", "B");
        });
    }

    @Test
    void whenSplit_givenClosingHashes_thenLeaveThemOutOfTheHeadingText() {
        List<Chunk> chunks = splitter.split(NOTE, "# A #\ntext");

        assertThat(chunks).singleElement()
                .extracting(chunk -> chunk.headings().segments()).isEqualTo(List.of("A"));
    }

    @Test
    void whenSplit_givenHashWithoutPrecedingSpace_thenKeepItInTheHeadingText() {
        List<Chunk> chunks = splitter.split(NOTE, "# C#\ntext");

        assertThat(chunks).singleElement()
                .extracting(chunk -> chunk.headings().segments()).isEqualTo(List.of("C#"));
    }

    @Test
    void whenSplit_givenLongWhitespaceInHeadingLine_thenFinishQuickly() {
        String markdown = "# " + " ".repeat(50_000) + "x";

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> splitter.split(NOTE, markdown));
    }

    @Test
    void whenSplit_givenFrontmatter_thenKeepItOutOfEveryChunk() {
        List<Chunk> chunks = splitter.split(NOTE, """
                ---
                title: Intervals
                ---
                # A
                text
                """);

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.content()).isEqualTo("text");
            assertThat(chunk.headings().segments()).containsExactly("A");
        });
    }

    @Test
    void whenSplit_givenUnclosedFrontmatterFence_thenTreatItAsText() {
        List<Chunk> chunks = splitter.split(NOTE, """
                ---
                # A
                text
                """);

        assertThat(chunks).extracting(Chunk::content).containsExactly("---", "text");
    }

    @Test
    void whenSplit_givenHeadingInsideCodeFence_thenKeepItAsContent() {
        List<Chunk> chunks = splitter.split(NOTE, """
                # A
                ```
                # not a heading
                ```
                after
                """);

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.content()).isEqualTo("```\n# not a heading\n```\nafter");
            assertThat(chunk.headings().segments()).containsExactly("A");
        });
    }
}
