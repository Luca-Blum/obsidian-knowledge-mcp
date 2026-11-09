package io.github.lucablum.knowledge.domain.link;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class LinkParserTest {

    private final LinkParser parser = new LinkParser();

    @Test
    void whenParse_givenWikilinkWithAliasAndHeading_thenKeepTargetAndAnchorAsWritten() {
        assertThat(parser.parse("Read [[Clean Architecture#Dependency Rule|the rule]] first."))
                .containsExactly(new Link("Clean Architecture", LinkKind.NOTE, Optional.of("Dependency Rule")));
    }

    @Test
    void whenParse_givenWikilinkWithoutAnchor_thenAnchorIsEmpty() {
        assertThat(parser.parse("See [[Coaching/Seiler]]."))
                .containsExactly(new Link("Coaching/Seiler", LinkKind.NOTE, Optional.empty()));
    }

    @Test
    void whenParse_givenEmbedsOfNoteAndImage_thenBothAreEmbeds() {
        assertThat(parser.parse("![[Zones#Table]]\n![[charts/zones.png|300]]")).containsExactly(
                new Link("Zones", LinkKind.EMBED, Optional.of("Table")),
                new Link("charts/zones.png", LinkKind.EMBED, Optional.empty()));
    }

    @Test
    void whenParse_givenLinkToAttachment_thenItIsFileLinkButDottedNoteNameIsNot() {
        assertThat(parser.parse("[[_sources/Seiler 2010.PDF#page=3]] and [[Dr. Seuss]]")).containsExactly(
                new Link("_sources/Seiler 2010.PDF", LinkKind.FILE, Optional.of("page=3")),
                new Link("Dr. Seuss", LinkKind.NOTE, Optional.empty()));
    }

    @Test
    void whenParse_givenSourceInFrontmatter_thenItIsFileLinkWhetherBracketedOrNot() {
        assertThat(parser.parse("""
                ---
                type: source
                source: "[[_sources/clean-architecture.pdf]]"
                related: "[[Ignored]]"
                ---
                Text""")).containsExactly(new Link("_sources/clean-architecture.pdf", LinkKind.FILE, Optional.empty()));
        assertThat(parser.parse("---\nsource: _sources/seiler.pdf\n---\n"))
                .containsExactly(new Link("_sources/seiler.pdf", LinkKind.FILE, Optional.empty()));
    }

    @Test
    void whenParse_givenWikilinksInCodeBlockAndInlineCode_thenIgnoreThem() {
        assertThat(parser.parse("""
                Write `[[Inline]]` for a link.
                ```markdown
                [[Fenced]]
                ```
                [[Real]]""")).containsExactly(new Link("Real", LinkKind.NOTE, Optional.empty()));
    }

    @Test
    void whenParse_givenLinkToOwnHeading_thenIgnoreIt() {
        assertThat(parser.parse("Back to [[#Summary]]")).isEmpty();
    }

    @Test
    void whenParse_givenSameLinkTwice_thenKeepItOnce() {
        assertThat(parser.parse("[[Seiler]] and again [[Seiler|him]]"))
                .containsExactly(new Link("Seiler", LinkKind.NOTE, Optional.empty()));
    }
}
