package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.application.indexing.IndexVault;
import io.github.lucablum.knowledge.application.reading.ReadNote;
import io.github.lucablum.knowledge.domain.chunk.section.HeadingPath;
import io.github.lucablum.knowledge.domain.note.NotePath;
import io.github.lucablum.knowledge.domain.reading.NoteOutline;
import io.github.lucablum.knowledge.domain.reading.NoteSections;
import io.github.lucablum.knowledge.domain.reading.WholeNote;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.text.Normalizer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Reading answers from the index alone: what is not indexed does not exist, whatever the Vault holds. */
@QuarkusTest
class ReadNoteTest {

    private static final String SHORT_NOTE = """
            ---
            title: Intervals
            ---
            ## Warm-up
            Fifteen minutes easy.
            """;

    @Inject
    TestVault vault;

    @Inject
    IndexVault indexVault;

    @Inject
    ReadNote readNote;

    @BeforeEach
    void emptyVault() {
        vault.clear();
    }

    @Test
    void whenRead_givenShortNote_thenReturnWholeTextWithFrontmatter() {
        vault.write("Running/Intervals.md", SHORT_NOTE);
        indexVault.run();

        assertThat(readNote.read(new NotePath("Running/Intervals.md"))).contains(new WholeNote(SHORT_NOTE));
    }

    @Test
    void whenRead_givenLongNote_thenReturnOutlineAndBeginning() {
        vault.write("Running/Marathon.md", """
                # Marathon
                Sixteen weeks.
                ## Base
                %s
                ## Taper
                Three weeks.
                """.formatted("Easy miles. ".repeat(30)));
        indexVault.run();

        assertThat(readNote.read(new NotePath("Running/Marathon.md"))).contains(new NoteOutline(
                List.of(
                        new HeadingPath(List.of("Marathon")),
                        new HeadingPath(List.of("Marathon", "Base")),
                        new HeadingPath(List.of("Marathon", "Taper"))),
                "# Marathon\nSixteen weeks.\n## Base"));
    }

    @Test
    void whenRead_givenPathInDecomposedUnicode_thenFindTheNoteByItsNfcKey() {
        vault.write("Läufe/Intervalle.md", "Kurz und hart.");
        indexVault.run();

        String decomposed = Normalizer.normalize("Läufe/Intervalle.md", Normalizer.Form.NFD);
        assertThat(readNote.read(new NotePath(decomposed))).contains(new WholeNote("Kurz und hart."));
    }

    @Test
    void whenRead_givenPathThatOnlyResolvesToANote_thenReturnNotFound() {
        vault.write("Running/Intervals.md", SHORT_NOTE);
        indexVault.run();

        assertThat(readNote.read(new NotePath("Running/../Running/Intervals.md"))).isEmpty();
        assertThat(readNote.read(new NotePath("./Running/Intervals.md"))).isEmpty();
        assertThat(readNote.read(new NotePath("running/intervals.md"))).isEmpty();
    }

    @Test
    void whenRead_givenFileInSkippedFolder_thenReturnNotFound() {
        vault.write("_eval/questions.md", "Which interval session builds speed?");
        indexVault.run();

        assertThat(readNote.read(new NotePath("_eval/questions.md"))).isEmpty();
    }

    @Test
    void whenRead_givenNoteChangedInVaultSinceIndexing_thenReturnIndexedText() {
        vault.write("Running/Intervals.md", SHORT_NOTE);
        indexVault.run();
        vault.write("Running/Intervals.md", "Changed but not indexed yet.");
        vault.write("Running/New.md", "Not indexed yet.");

        assertThat(readNote.read(new NotePath("Running/Intervals.md"))).contains(new WholeNote(SHORT_NOTE));
        assertThat(readNote.read(new NotePath("Running/New.md"))).isEmpty();
    }

    @Test
    void whenReadSections_givenHeadingPathOfSeveralSections_thenReturnAllOfThem() {
        vault.write("Running/Week.md", """
                ## Monday
                ### Notes
                Easy run.
                ## Tuesday
                ### Notes
                Intervals.
                ### Notes
                Felt good.
                """);
        indexVault.run();

        HeadingPath notes = new HeadingPath(List.of("Tuesday", "Notes"));
        assertThat(readNote.read(new NotePath("Running/Week.md"), notes)).contains(
                new NoteSections(notes, List.of("### Notes\nIntervals.", "### Notes\nFelt good.")));
    }

    @Test
    void whenReadSections_givenNoteNotIndexed_thenReturnNotFound() {
        assertThat(readNote.read(new NotePath("Running/Missing.md"), new HeadingPath(List.of("Notes")))).isEmpty();
    }
}
