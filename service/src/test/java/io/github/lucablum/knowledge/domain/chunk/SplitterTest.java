package io.github.lucablum.knowledge.domain.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.github.lucablum.knowledge.domain.note.Note;
import io.github.lucablum.knowledge.domain.note.NoteKind;
import io.github.lucablum.knowledge.domain.note.NoteLanguage;
import io.github.lucablum.knowledge.domain.note.NotePath;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SplitterTest {

    private static final Note NOTE = new Note(new NotePath("Running/Intervals.md"), NoteKind.OWN, "Interval Training",
            Optional.empty(), Optional.empty(), Optional.empty(), new NoteLanguage("en"));

    /** Splits at every heading level and never merges, so each test sets only the rule it is about. */
    private static final ChunkingRules EVERY_SECTION = new ChunkingRules(6, 0, 2000, 0);

    private final Splitter splitter = new Splitter(EVERY_SECTION);

    @Test
    void whenSplit_givenHeadingBelowConfiguredLevel_thenKeepItInsideItsSection() {
        Splitter atH2 = new Splitter(new ChunkingRules(2, 0, 2000, 0));

        List<Chunk> chunks = atH2.split(NOTE, """
                ## B
                body b
                ### C
                body c
                ## D
                body d
                """);

        assertThat(chunks).extracting(Chunk::content).containsExactly("body b\n### C\nbody c", "body d");
        assertThat(chunks).extracting(chunk -> chunk.headings().segments())
                .containsExactly(List.of("B"), List.of("D"));
    }

    @Test
    void whenSplit_givenSectionBelowMinimum_thenMergeItWithTheNextUnderTheirCommonHeadingPath() {
        Splitter merging = new Splitter(new ChunkingRules(6, 20, 2000, 0));

        List<Chunk> chunks = merging.split(NOTE, """
                # A
                ## B
                short
                ## C
                a body long enough to stand alone
                """);

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.content()).isEqualTo("## B\nshort\n\n## C\na body long enough to stand alone");
            assertThat(chunk.headings().segments()).containsExactly("A");
        });
    }

    @Test
    void whenSplit_givenLastSectionBelowMinimum_thenMergeItWithThePrevious() {
        Splitter merging = new Splitter(new ChunkingRules(6, 20, 2000, 0));

        List<Chunk> chunks = merging.split(NOTE, """
                # A
                a body long enough to stand alone
                ## B
                short
                """);

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.content()).isEqualTo("a body long enough to stand alone\n\n## B\nshort");
            assertThat(chunk.headings().segments()).containsExactly("A");
        });
    }

    @Test
    void whenSplit_givenSectionAboveMaximum_thenSplitItAtParagraphsUnderItsHeadingPath() {
        Splitter splitting = new Splitter(new ChunkingRules(6, 0, 50, 0));

        List<Chunk> chunks = splitting.split(NOTE, """
                # A
                first paragraph with some words

                second paragraph with more words

                third one
                """);

        assertThat(chunks).extracting(Chunk::ordinal).containsExactly(0, 1);
        assertThat(chunks).extracting(Chunk::content)
                .containsExactly("first paragraph with some words", "second paragraph with more words\n\nthird one");
        assertThat(chunks).extracting(chunk -> chunk.headings().segments())
                .containsExactly(List.of("A"), List.of("A"));
    }

    @Test
    void whenSplit_givenOverlap_thenStartEachPieceWithTheWholeWordsEndingThePrevious() {
        Splitter overlapping = new Splitter(new ChunkingRules(6, 0, 50, 12));

        List<Chunk> chunks = overlapping.split(NOTE, """
                first paragraph with some words

                second paragraph with more words

                third one
                """);

        assertThat(chunks).extracting(Chunk::content).containsExactly(
                "first paragraph with some words",
                "some words\n\nsecond paragraph with more words",
                "more words\n\nthird one");
    }

    @Test
    void whenSplit_givenCodeBlockAboveMaximum_thenKeepItWhole() {
        Splitter splitting = new Splitter(new ChunkingRules(6, 0, 30, 0));

        List<Chunk> chunks = splitting.split(NOTE, """
                intro paragraph here

                ```
                first line of code

                second line of code
                ```
                """);

        assertThat(chunks).extracting(Chunk::content).containsExactly(
                "intro paragraph here", "```\nfirst line of code\n\nsecond line of code\n```");
    }

    @Test
    void whenSplit_givenTableBeforeTheSplit_thenKeepItWholeAndRepeatNoneOfIt() {
        Splitter overlapping = new Splitter(new ChunkingRules(6, 0, 40, 10));

        List<Chunk> chunks = overlapping.split(NOTE, """
                | pace | effort |
                | ---- | ------ |
                | easy | low    |

                after the table comes text
                """);

        assertThat(chunks).extracting(Chunk::content).containsExactly(
                "| pace | effort |\n| ---- | ------ |\n| easy | low    |", "after the table comes text");
    }

    @Test
    void whenSplit_givenCodeBlockBeforeTheSplit_thenRepeatNoneOfIt() {
        Splitter overlapping = new Splitter(new ChunkingRules(6, 0, 30, 10));

        List<Chunk> chunks = overlapping.split(NOTE, """
                ```
                int pace = easy();
                ```

                after the code comes text
                """);

        assertThat(chunks).extracting(Chunk::content)
                .containsExactly("```\nint pace = easy();\n```", "after the code comes text");
    }

    @Test
    void whenSplit_givenDuplicateHeadingsAndSplitSection_thenNumberEveryChunkUniquelyInOrder() {
        Splitter splitting = new Splitter(new ChunkingRules(6, 0, 40, 0));

        List<Chunk> chunks = splitting.split(NOTE, """
                ## Notes
                first paragraph of the first notes

                second paragraph of the first notes
                ## Notes
                the second notes section
                """);

        assertThat(chunks).extracting(Chunk::ordinal).containsExactly(0, 1, 2);
        assertThat(chunks).extracting(chunk -> chunk.headings().segments())
                .containsExactly(List.of("Notes"), List.of("Notes"), List.of("Notes"));
    }

    @Test
    void whenEmbeddingText_givenNoteWithTitle_thenPrefixTopicTitleAndHeadingPathToTheContent() {
        List<Chunk> chunks = splitter.split(NOTE, """
                # A
                ## B
                text
                """);

        assertThat(chunks).singleElement().extracting(Chunk::embeddingText)
                .isEqualTo("Running > Interval Training > A > B\n\ntext");
    }

    @Test
    void whenEmbeddingText_givenWikilinks_thenReduceThemToTheirDisplayTextButKeepThemInTheContent() {
        String content = "see [[Clean Architecture|CA]], [[Dependency Rule]], [[Entities#Rules]] and ![[diagram.png]]";

        List<Chunk> chunks = splitter.split(NOTE, "# About [[Clean Architecture|CA]]\n" + content);

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.embeddingText()).isEqualTo("Running > Interval Training > About CA\n\n"
                    + "see CA, Dependency Rule, Entities > Rules and diagram.png");
            assertThat(chunk.content()).isEqualTo(content);
        });
    }

    @Test
    void whenSplit_givenSmallSectionsWithTheSameHeading_thenKeepTheSecondHeadingInTheMergedContent() {
        Splitter merging = new Splitter(new ChunkingRules(6, 20, 2000, 0));

        List<Chunk> chunks = merging.split(NOTE, """
                ## Notes
                first
                ## Notes
                second
                """);

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.content()).isEqualTo("first\n\n## Notes\nsecond");
            assertThat(chunk.headings().segments()).containsExactly("Notes");
        });
    }

    @Test
    void whenSplit_givenOtherFenceInsideCodeBlock_thenKeepTheCodeBlockWhole() {
        Splitter splitting = new Splitter(new ChunkingRules(6, 0, 20, 0));

        List<Chunk> chunks = splitting.split(NOTE, """
                ````
                ```
                # not a heading

                ~~~
                ````
                """);

        assertThat(chunks).singleElement().extracting(Chunk::content)
                .isEqualTo("````\n```\n# not a heading\n\n~~~\n````");
    }

    @Test
    void whenCreateRules_givenNegativeOverlap_thenReject() {
        assertThatThrownBy(() -> new ChunkingRules(3, 200, 2000, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whenCreateRules_givenMinimumAboveMaximum_thenReject() {
        assertThatThrownBy(() -> new ChunkingRules(3, 2000, 200, 0)).isInstanceOf(IllegalArgumentException.class);
    }

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
