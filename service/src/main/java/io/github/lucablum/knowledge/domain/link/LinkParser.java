package io.github.lucablum.knowledge.domain.link;

import io.github.lucablum.knowledge.domain.chunk.markdown.CodeBlock;
import io.github.lucablum.knowledge.domain.chunk.markdown.HeadingLine;
import io.github.lucablum.knowledge.domain.chunk.markdown.MarkdownLine;
import io.github.lucablum.knowledge.domain.chunk.markdown.MarkdownLines;
import io.github.lucablum.knowledge.domain.chunk.markdown.TextLine;
import io.github.lucablum.knowledge.domain.note.Frontmatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
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
        for (MarkdownLine line : MarkdownLines.of(markdown)) {
            Matcher wikilink = WIKILINK.matcher(INLINE_CODE.matcher(text(line)).replaceAll(""));
            while (wikilink.find()) {
                LinkKind kind = wikilink.group("embed").isEmpty() ? LinkKind.NOTE : LinkKind.EMBED;
                Link link = link(wikilink.group("target"), kind);
                if (!link.target().isBlank()) {
                    links.add(link);
                }
            }
        }
        return List.copyOf(links);
    }

    private static String text(MarkdownLine line) {
        return switch (line) {
            case HeadingLine heading -> heading.line();
            case TextLine text -> text.value();
            case CodeBlock code -> "";
        };
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

    /**
     * A note link to an attachment is a file link; an embed stays an embed, whatever it shows. In a table the pipe
     * before an alias is escaped ({@code [[Note\\|alias]]}), and that backslash is no part of the target.
     */
    private static Link link(String written, LinkKind kind) {
        String unescaped = written.endsWith("\\") ? written.substring(0, written.length() - 1) : written;
        int hash = unescaped.indexOf('#');
        String target = hash < 0 ? unescaped : unescaped.substring(0, hash);
        Optional<String> anchor = hash < 0 ? Optional.empty() : Optional.of(unescaped.substring(hash + 1));
        boolean toFile = kind == LinkKind.NOTE && Attachments.isAttachment(target);
        return new Link(target, toFile ? LinkKind.FILE : kind, anchor);
    }
}
