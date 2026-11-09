package io.github.lucablum.knowledge.domain.reading;

import io.github.lucablum.knowledge.domain.chunk.markdown.CodeBlock;
import io.github.lucablum.knowledge.domain.chunk.markdown.HeadingLine;
import io.github.lucablum.knowledge.domain.chunk.markdown.MarkdownLine;
import io.github.lucablum.knowledge.domain.chunk.markdown.MarkdownLines;
import io.github.lucablum.knowledge.domain.chunk.markdown.TextLine;
import io.github.lucablum.knowledge.domain.chunk.section.HeadingPath;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A Note's full raw text as it was indexed, frontmatter included. Headings are tracked at every level, so the heading
 * path of a heading is the one a Search Hit shows for the section it opens.
 */
@ValueObject
public record NoteText(String markdown) {

    public NoteText {
        Objects.requireNonNull(markdown);
    }

    /** The whole Note if it fits within the maximum, otherwise its outline and beginning. */
    public NoteReading read(ReadingRules rules) {
        if (markdown.length() <= rules.maximumCharacters()) {
            return new WholeNote(markdown);
        }
        return new NoteOutline(outline(), beginning(rules.beginningCharacters()));
    }

    /**
     * Every section whose heading has {@code headings} as its path, in order and as written. A section runs up to the
     * next heading of its own or a higher level, so it holds the deeper headings below it.
     */
    public List<String> sections(HeadingPath headings) {
        List<String> sections = new ArrayList<>();
        Deque<HeadingLine> open = new ArrayDeque<>();
        List<String> section = null;
        int sectionLevel = 0;
        for (MarkdownLine line : MarkdownLines.of(markdown)) {
            if (line instanceof HeadingLine heading) {
                if (section != null && heading.level() <= sectionLevel) {
                    sections.add(String.join("\n", section).strip());
                    section = null;
                }
                if (open(open, heading).equals(headings)) {
                    section = new ArrayList<>();
                    sectionLevel = heading.level();
                }
            }
            if (section != null) {
                section.addAll(lines(line));
            }
        }
        if (section != null) {
            sections.add(String.join("\n", section).strip());
        }
        return sections;
    }

    private List<HeadingPath> outline() {
        List<HeadingPath> outline = new ArrayList<>();
        Deque<HeadingLine> open = new ArrayDeque<>();
        for (MarkdownLine line : MarkdownLines.of(markdown)) {
            if (line instanceof HeadingLine heading) {
                outline.add(open(open, heading));
            }
        }
        return outline;
    }

    /** Opens {@code heading}, which closes every open heading of the same or a deeper level, and returns its path. */
    private static HeadingPath open(Deque<HeadingLine> open, HeadingLine heading) {
        while (!open.isEmpty() && open.peekLast().level() >= heading.level()) {
            open.removeLast();
        }
        open.addLast(heading);
        return new HeadingPath(open.stream().map(HeadingLine::text).toList());
    }

    private static List<String> lines(MarkdownLine line) {
        return switch (line) {
            case HeadingLine heading -> List.of(heading.line());
            case TextLine text -> List.of(text.value());
            case CodeBlock code -> code.lines();
        };
    }

    /** At most {@code characters} from the start, cut at the last line break, or else between words. */
    private String beginning(int characters) {
        String start = markdown.substring(0, characters);
        int lineBreak = start.lastIndexOf('\n');
        if (lineBreak > 0) {
            return start.substring(0, lineBreak).stripTrailing();
        }
        int space = start.lastIndexOf(' ', start.length() - 1);
        boolean cutInsideWord = !Character.isWhitespace(markdown.charAt(characters)) && space > 0;
        return (cutInsideWord ? start.substring(0, space) : start).stripTrailing();
    }
}
