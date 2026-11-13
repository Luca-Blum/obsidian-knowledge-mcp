package io.github.lucablum.knowledge.domain.link;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkResolutionTest {

    private final LinkResolution resolution = new LinkResolution(List.of(
            new NotePath("Running/Source-Notes/Seiler.md"),
            new NotePath("Running/Zones.md"),
            new NotePath("Cycling/Zones.md"),
            new NotePath("Archive/Running/Zones.md")));

    @Test
    void whenResolve_givenFileNameInAnotherCase_thenMatchThatNote() {
        assertThat(resolution.resolve("seiler")).containsExactly(new NotePath("Running/Source-Notes/Seiler.md"));
    }

    @Test
    void whenResolve_givenPathSuffixWithOrWithoutExtension_thenMatchOnlyTheNoteWithThatSuffix() {
        assertThat(resolution.resolve("cycling/zones")).containsExactly(new NotePath("Cycling/Zones.md"));
        assertThat(resolution.resolve("Cycling/Zones.md")).containsExactly(new NotePath("Cycling/Zones.md"));
        assertThat(resolution.resolve("/Source-Notes/Seiler")).containsExactly(
                new NotePath("Running/Source-Notes/Seiler.md"));
    }

    @Test
    void whenResolve_givenSuffixThatEndsInsideAFolderName_thenMatchNothing() {
        assertThat(resolution.resolve("Notes/Seiler")).isEmpty();
        assertThat(resolution.resolve("ler")).isEmpty();
    }

    @Test
    void whenResolve_givenFileNameOfSeveralNotes_thenMatchAllOfThem() {
        assertThat(resolution.resolve("ZONES")).containsExactlyInAnyOrder(new NotePath("Running/Zones.md"),
                new NotePath("Cycling/Zones.md"), new NotePath("Archive/Running/Zones.md"));
    }

    @Test
    void whenResolve_givenWholeVaultPathThatIsAlsoSuffixOfAnother_thenMatchOnlyTheWholePath() {
        assertThat(resolution.resolve("Running/Zones")).containsExactly(new NotePath("Running/Zones.md"));
    }
}
