package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.application.indexing.IndexVault;
import io.github.lucablum.knowledge.application.indexing.Reconciled;
import io.github.lucablum.knowledge.application.link.FollowLinks;
import io.github.lucablum.knowledge.domain.link.AmbiguousLink;
import io.github.lucablum.knowledge.domain.link.Backlink;
import io.github.lucablum.knowledge.domain.link.Link;
import io.github.lucablum.knowledge.domain.link.LinkKind;
import io.github.lucablum.knowledge.domain.link.Related;
import io.github.lucablum.knowledge.domain.link.ResolvedLink;
import io.github.lucablum.knowledge.domain.note.NotePath;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RelatedTest {

    @Inject
    TestVault vault;

    @Inject
    IndexVault indexVault;

    @Inject
    FollowLinks links;

    @BeforeEach
    void emptyVault() {
        vault.clear();
    }

    @Test
    void whenRelated_givenLinkToNote_thenReturnItResolvedWithItsAnchor() {
        vault.write("Running/Plan.md", "Based on [[seiler#Polarized|Seiler]].");
        vault.write("Running/Source-Notes/Seiler.md", "Polarized training");
        indexVault.run();

        assertThat(related("Running/Plan.md").links()).containsExactly(new ResolvedLink(
                new Link("seiler", LinkKind.NOTE, Optional.of("Polarized")),
                new NotePath("Running/Source-Notes/Seiler.md")));
    }

    @Test
    void whenRelated_givenLinksOfEveryKind_thenSortThemIntoTheirCategories() {
        vault.write("Running/Source-Notes/Seiler.md", """
                ---
                type: source
                source: "[[_sources/seiler.pdf]]"
                ---
                ![[Zones#Table]] ![[charts/zones.png]] [[Intervals]] [[Missing#Gone]]""");
        vault.write("Running/Zones.md", "Running zones");
        vault.write("Running/Intervals.md", "Running intervals");
        vault.write("Cycling/Intervals.md", "Cycling intervals");
        indexVault.run();

        Related related = related("Running/Source-Notes/Seiler.md");

        assertThat(related.links()).containsExactly(new ResolvedLink(
                new Link("Zones", LinkKind.EMBED, Optional.of("Table")), new NotePath("Running/Zones.md")));
        assertThat(related.files()).containsExactly(
                new Link("_sources/seiler.pdf", LinkKind.FILE, Optional.empty()),
                new Link("charts/zones.png", LinkKind.EMBED, Optional.empty()));
        assertThat(related.unresolved()).containsExactly(new Link("Missing", LinkKind.NOTE, Optional.of("Gone")));
        assertThat(related.ambiguous()).containsExactly(new AmbiguousLink(
                new Link("Intervals", LinkKind.NOTE, Optional.empty()),
                List.of(new NotePath("Cycling/Intervals.md"), new NotePath("Running/Intervals.md"))));
    }

    @Test
    void whenRelated_givenOtherNotesLinkToIt_thenReturnThoseAsBacklinksButNotAmbiguousOnes() {
        vault.write("Running/Source-Notes/Seiler.md", "Polarized training");
        vault.write("Running/Plan.md", "[[Running/Source-Notes/Seiler#Polarized]] and [[Running/Source-Notes/Seiler]]");
        vault.write("Running/Zones.md", "![[source-notes/seiler]]");
        vault.write("Cycling/Seiler.md", "Another Seiler");
        vault.write("Cycling/Plan.md", "[[Seiler]]");
        indexVault.run();

        assertThat(related("Running/Source-Notes/Seiler.md").backlinks()).containsExactly(
                new Backlink(new NotePath("Running/Plan.md"),
                        new Link("Running/Source-Notes/Seiler", LinkKind.NOTE, Optional.of("Polarized"))),
                new Backlink(new NotePath("Running/Plan.md"),
                        new Link("Running/Source-Notes/Seiler", LinkKind.NOTE, Optional.empty())),
                new Backlink(new NotePath("Running/Zones.md"),
                        new Link("source-notes/seiler", LinkKind.EMBED, Optional.empty())));
    }

    @Test
    void whenRelated_givenLinkedNoteCreatedLater_thenLinkResolvesWithoutIndexingTheLinkingNoteAgain() {
        vault.write("Running/Plan.md", "[[Seiler]]");
        indexVault.run();
        assertThat(related("Running/Plan.md").unresolved()).extracting(Link::target).containsExactly("Seiler");

        vault.write("Running/Seiler.md", "Polarized training");
        Reconciled reconciled = indexVault.run();

        assertThat(reconciled).isEqualTo(new Reconciled(1, 0, 1, List.of()));
        assertThat(related("Running/Plan.md").links()).extracting(ResolvedLink::note)
                .containsExactly(new NotePath("Running/Seiler.md"));
        assertThat(related("Running/Seiler.md").backlinks()).extracting(Backlink::from)
                .containsExactly(new NotePath("Running/Plan.md"));
    }

    @Test
    void whenRelated_givenLinkedNoteRenamedOrDeleted_thenBacklinkMovesOrLinkIsUnresolved() {
        vault.write("Running/Plan.md", "[[Seiler]]");
        vault.write("Running/Seiler.md", "Polarized training");
        indexVault.run();

        vault.clear();
        vault.write("Running/Plan.md", "[[Seiler]]");
        vault.write("Running/Sources/Seiler.md", "Polarized training");
        assertThat(indexVault.run()).isEqualTo(new Reconciled(1, 1, 1, List.of()));
        assertThat(related("Running/Sources/Seiler.md").backlinks()).extracting(Backlink::from)
                .containsExactly(new NotePath("Running/Plan.md"));

        vault.clear();
        vault.write("Running/Plan.md", "[[Seiler]]");
        assertThat(indexVault.run()).isEqualTo(new Reconciled(0, 1, 1, List.of()));
        assertThat(related("Running/Plan.md").unresolved()).extracting(Link::target).containsExactly("Seiler");
    }

    @Test
    void whenRelated_givenNoteChangedItsLinks_thenReturnOnlyTheNewOnes() {
        vault.write("Running/Plan.md", "[[Seiler]]");
        indexVault.run();
        vault.write("Running/Plan.md", "[[Daniels]]");
        indexVault.run();

        assertThat(related("Running/Plan.md").unresolved()).extracting(Link::target).containsExactly("Daniels");
    }

    @Test
    void whenRelated_givenNoteThatIsNotIndexed_thenReturnNothing() {
        vault.write("_eval/Questions.md", "[[Seiler]]");
        indexVault.run();

        assertThat(links.related(new NotePath("_eval/Questions.md"))).isEmpty();
        assertThat(links.related(new NotePath("Running/../_eval/Questions.md"))).isEmpty();
    }

    private Related related(String path) {
        return links.related(new NotePath(path)).orElseThrow();
    }
}
