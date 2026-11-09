package io.github.lucablum.knowledge.domain.reading;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.domain.chunk.section.HeadingPath;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoteTextTest {

    private static final ReadingRules RULES = new ReadingRules(100, 30);

    @Test
    void whenRead_givenNoteWithinMaximum_thenReturnWholeTextWithFrontmatter() {
        String markdown = "---\ntitle: Intervals\n---\n# Intervals\nShort and hard.\n";

        assertThat(new NoteText(markdown).read(RULES)).isEqualTo(new WholeNote(markdown));
    }

    @Test
    void whenRead_givenNoteAboveMaximum_thenReturnOutlineOfEveryHeadingAndBeginningCutAtALineBreak() {
        String markdown = """
                # Intervals
                Short and hard.
                ## Warm-up
                Fifteen minutes easy, then strides.
                ### Strides
                Four times twenty seconds.
                ## Main set
                Five times four minutes at threshold.
                """;

        assertThat(new NoteText(markdown).read(RULES)).isEqualTo(new NoteOutline(
                List.of(
                        new HeadingPath(List.of("Intervals")),
                        new HeadingPath(List.of("Intervals", "Warm-up")),
                        new HeadingPath(List.of("Intervals", "Warm-up", "Strides")),
                        new HeadingPath(List.of("Intervals", "Main set"))),
                "# Intervals\nShort and hard."));
    }

    @Test
    void whenRead_givenFirstLineLongerThanBeginning_thenCutBeginningAtWholeWords() {
        String markdown = "Intervals are short and hard repetitions with rest in between. ".repeat(3);

        assertThat(new NoteText(markdown).read(RULES))
                .isEqualTo(new NoteOutline(List.of(), "Intervals are short and hard"));
    }

    @Test
    void whenRead_givenHeadingInsideCodeBlockOrFrontmatter_thenLeaveItOutOfTheOutline() {
        String markdown = """
                ---
                # not a heading
                ---
                ## Script
                ```bash
                # a comment
                ```
                """ + "x".repeat(100);

        assertThat(new NoteText(markdown).read(RULES)).isInstanceOfSatisfying(NoteOutline.class,
                outline -> assertThat(outline.headings()).containsExactly(new HeadingPath(List.of("Script"))));
    }

    @Test
    void whenSections_givenHeadingPath_thenReturnSectionAsWrittenWithDeeperHeadingsUpToTheNextOfItsLevel() {
        NoteText text = new NoteText("""
                # Intervals
                ## Warm-up
                Fifteen minutes easy.

                ### Strides
                Four times twenty seconds.
                ```
                # not a heading
                ```

                ## Main set
                Five times four minutes.
                """);

        assertThat(text.sections(new HeadingPath(List.of("Intervals", "Warm-up")))).containsExactly("""
                ## Warm-up
                Fifteen minutes easy.

                ### Strides
                Four times twenty seconds.
                ```
                # not a heading
                ```""");
    }

    @Test
    void whenSections_givenHeadingPathOfSeveralSections_thenReturnAllOfThemInOrder() {
        NoteText text = new NoteText("""
                ## Monday
                ### Notes
                Easy run.
                ## Tuesday
                ### Notes
                Intervals.
                ### Notes
                Felt good.
                """);

        assertThat(text.sections(new HeadingPath(List.of("Tuesday", "Notes"))))
                .containsExactly("### Notes\nIntervals.", "### Notes\nFelt good.");
    }

    @Test
    void whenSections_givenHeadingPathMatchingNoHeading_thenReturnNone() {
        NoteText text = new NoteText("## Monday\n### Notes\nEasy run.\n");

        assertThat(text.sections(new HeadingPath(List.of("Notes")))).isEmpty();
    }
}
