package io.github.lucablum.knowledge.domain.reading;

import io.github.lucablum.knowledge.domain.markdown.CodeBlock;
import io.github.lucablum.knowledge.domain.markdown.HeadingLine;
import io.github.lucablum.knowledge.domain.markdown.HeadingPath;
import io.github.lucablum.knowledge.domain.markdown.MarkdownLine;
import io.github.lucablum.knowledge.domain.markdown.MarkdownLines;
import io.github.lucablum.knowledge.domain.markdown.OpenHeadings;
import io.github.lucablum.knowledge.domain.markdown.TextLine;
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

    private static List<String> sectionsUnder(HeadingPath path, List<MarkdownLine> lines) {
        List<Heading> headings = headings(lines);
        return headings.stream()
                .filter(heading -> heading.path().equals(path))
                .map(heading -> text(lines.subList(heading.index(), sectionEnd(heading, headings, lines.size()))))
                .toList();
    }

    /** Where the next heading of the same or a higher level stands, or the end of the Note. */
    private static int sectionEnd(Heading heading, List<Heading> headings, int endOfNote) {
        return headings.stream()
                .filter(next -> next.index() > heading.index() && next.level() <= heading.level())
                .mapToInt(Heading::index)
                .findFirst()
                .orElse(endOfNote);
    }

    private List<HeadingPath> outline() {
        return headings(MarkdownLines.of(markdown)).stream().map(Heading::path).toList();
    }

    /** Every heading of the Note, in order. */
    private static List<Heading> headings(List<MarkdownLine> lines) {
        List<Heading> headings = new ArrayList<>();
        OpenHeadings open = OpenHeadings.NONE;
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index) instanceof HeadingLine line) {
                open = open.open(line);
                headings.add(new Heading(index, line.level(), open.path()));
            }
        }
        return headings;
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
        return markdown.substring(0, cut(characters)).stripTrailing();
    }

    /** The last line break before {@code characters}; without one, the last space if the cut would split a word. */
    private int cut(int characters) {
        String start = markdown.substring(0, characters);
        int lineBreak = start.lastIndexOf('\n');
        if (lineBreak > 0) {
            return lineBreak;
        }
        int space = start.lastIndexOf(' ');
        if (space > 0 && splitsWord(characters)) {
            return space;
        }
        return characters;
    }

    private boolean splitsWord(int cut) {
        return !Character.isWhitespace(markdown.charAt(cut));
    }

    /** A heading with the index of its line among the Note's lines and its heading path. */
    private record Heading(int index, int level, HeadingPath path) {}
}
