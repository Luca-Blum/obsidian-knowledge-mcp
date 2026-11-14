package io.github.lucablum.knowledge.domain.link;

import io.github.lucablum.knowledge.domain.markdown.CodeBlock;
import io.github.lucablum.knowledge.domain.markdown.HeadingLine;
import io.github.lucablum.knowledge.domain.markdown.MarkdownLine;
import io.github.lucablum.knowledge.domain.markdown.MarkdownLines;
import io.github.lucablum.knowledge.domain.markdown.TextLine;
import io.github.lucablum.knowledge.domain.note.Frontmatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.Service;

/**
 * Reads the Links a Note's Markdown contains, as they are written: wikilinks and embeds in the body, and the
 * {@code source:} field of the frontmatter, which points to the file a Source Note was made from. Like Obsidian, it
 * skips code, and a link to a heading of the Note itself ({@code [[#Summary]]}) is no Link to anything else.
 */
@Service
public class LinkParser {

    private static final Pattern WIKILINK = Pattern.compile("(?<embed>!?)\\[\\[(?<target>[^\\]|]*)(?:\\|[^\\]]*)?]]");
    private static final Pattern INLINE_CODE = Pattern.compile("(`+).+?\\1");

    /** Each Link once, in the order it first appears. */
    public List<Link> parse(String markdown) {
        LinkedHashSet<Link> links = new LinkedHashSet<>();
        source(markdown).ifPresent(links::add);
        MarkdownLines.of(markdown).forEach(line -> links.addAll(wikilinks(line)));
        return List.copyOf(links);
    }

    /** The wikilinks and embeds of one line; one with an empty target points into the Note itself and is left out. */
    private static List<Link> wikilinks(MarkdownLine line) {
        return WIKILINK.matcher(withoutCode(line)).results()
                .map(LinkParser::link)
                .filter(link -> !link.target().isBlank())
                .toList();
    }

    private static String withoutCode(MarkdownLine line) {
        String text = switch (line) {
            case HeadingLine heading -> heading.line();
            case TextLine textLine -> textLine.value();
            case CodeBlock code -> "";
        };
        return INLINE_CODE.matcher(text).replaceAll("");
    }

    private static Link link(MatchResult wikilink) {
        LinkKind kind = wikilink.group("embed").isEmpty() ? LinkKind.NOTE : LinkKind.EMBED;
        return link(wikilink.group("target"), kind);
    }

    /** The {@code source:} field as a file Link, bracketed or not; frontmatter that cannot be read has none. */
    private static Optional<Link> source(String markdown) {
        Optional<String> source;
        try {
            source = Frontmatter.of(markdown).get("source");
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return source.map(value -> {
            Matcher wikilink = WIKILINK.matcher(value);
            return link(wikilink.find() ? wikilink.group("target") : value, LinkKind.FILE);
        });
    }

    /** {@code written} is the target with its anchor, as in {@code Note#Heading}. */
    private static Link link(String written, LinkKind kind) {
        String[] targetAndAnchor = withoutTableEscape(written).split("#", 2);
        String target = targetAndAnchor[0];
        Optional<String> anchor = targetAndAnchor.length == 2 ? Optional.of(targetAndAnchor[1]) : Optional.empty();
        return new Link(target, kindOf(target, kind), anchor);
    }

    /**
     * In a table the pipe before an alias is escaped ({@code [[Note\\|alias]]}), and that backslash is no part of the
     * target.
     */
    private static String withoutTableEscape(String written) {
        return written.endsWith("\\") ? written.substring(0, written.length() - 1) : written;
    }

    /** A note link to an attachment is a file link; an embed stays an embed, whatever it shows. */
    private static LinkKind kindOf(String target, LinkKind written) {
        return written == LinkKind.NOTE && Attachments.isAttachment(target) ? LinkKind.FILE : written;
    }
}
