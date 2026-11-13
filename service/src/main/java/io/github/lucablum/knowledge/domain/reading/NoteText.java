package io.github.lucablum.knowledge.domain.reading;

import io.github.lucablum.knowledge.domain.chunk.markdown.CodeBlock;
import io.github.lucablum.knowledge.domain.chunk.markdown.HeadingLine;
import io.github.lucablum.knowledge.domain.chunk.markdown.MarkdownLine;
import io.github.lucablum.knowledge.domain.chunk.markdown.MarkdownLines;
import io.github.lucablum.knowledge.domain.chunk.markdown.TextLine;
import io.github.lucablum.knowledge.domain.chunk.section.HeadingPath;
import io.github.lucablum.knowledge.domain.chunk.section.OpenHeadings;
import java.util.ArrayList;
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
     * Every section whose heading has {@code heading} as its path, in order and as written. A section runs up to the
     * next heading of its own or a higher level, so it holds the deeper headings below it. The empty path is the
     * section above every heading, which is the whole Note without its frontmatter.
     */
    public List<String> sections(HeadingPath heading) {
        List<MarkdownLine> lines = MarkdownLines.of(markdown);
        List<String> sections = heading.segments().isEmpty() ? List.of(text(lines)) : sectionsUnder(heading, lines);
        return sections.stream().filter(section -> !section.isEmpty()).toList();
    }

    private static List<String> sectionsUnder(HeadingPath heading, List<MarkdownLine> lines) {
        List<String> sections = new ArrayList<>();
        OpenHeadings open = OpenHeadings.NONE;
        for (int start = 0; start < lines.size(); start++) {
            if (lines.get(start) instanceof HeadingLine next) {
                open = open.open(next);
                if (open.path().equals(heading)) {
                    sections.add(text(lines.subList(start, sectionEnd(lines, start, next))));
                }
            }
        }
        return sections;
    }

    /** The index of the next heading of the same or a higher level than {@code heading}, or the end of the Note. */
    private static int sectionEnd(List<MarkdownLine> lines, int start, HeadingLine heading) {
        for (int end = start + 1; end < lines.size(); end++) {
            if (lines.get(end) instanceof HeadingLine next && next.level() <= heading.level()) {
                return end;
            }
        }
        return lines.size();
    }

    private List<HeadingPath> outline() {
        List<HeadingPath> outline = new ArrayList<>();
        OpenHeadings open = OpenHeadings.NONE;
        for (MarkdownLine line : MarkdownLines.of(markdown)) {
            if (line instanceof HeadingLine heading) {
                open = open.open(heading);
                outline.add(open.path());
            }
        }
        return outline;
    }

    /** The lines as written, stripped; a section without content, such as the body of an empty Note, is empty. */
    private static String text(List<MarkdownLine> lines) {
        return String.join("\n", lines.stream().flatMap(line -> written(line).stream()).toList()).strip();
    }

    private static List<String> written(MarkdownLine line) {
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
        int space = start.lastIndexOf(' ');
        boolean cutInsideWord = !Character.isWhitespace(markdown.charAt(characters)) && space > 0;
        return (cutInsideWord ? start.substring(0, space) : start).stripTrailing();
    }
}
