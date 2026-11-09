package io.github.lucablum.knowledge.domain.chunk.section;

import java.util.ArrayList;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A paragraph, table or code block of a section, as written, with the blank lines that follow it. A section is split
 * between blocks, never inside one.
 */
@ValueObject
public record Block(List<String> lines, Kind kind) {

    /** Only prose is repeated as overlap: a fragment of a table or of code would be unreadable on its own. */
    public enum Kind { PROSE, TABLE, CODE }

    public Block {
        lines = List.copyOf(lines);
    }

    /** A paragraph is a table if it holds a table row. */
    static Block paragraph(List<String> lines) {
        boolean table = lines.stream().anyMatch(line -> line.strip().startsWith("|"));
        return new Block(lines, table ? Kind.TABLE : Kind.PROSE);
    }

    static Block code(List<String> lines) {
        return new Block(lines, Kind.CODE);
    }

    Block withBlankLine(String blankLine) {
        List<String> more = new ArrayList<>(lines);
        more.add(blankLine);
        return new Block(more, kind);
    }

    /** As many whole words from the end of this block as fit into {@code characters}; none for a table or code. */
    public String lastWords(int characters) {
        if (kind != Kind.PROSE) {
            return "";
        }
        List<String> words = List.of(text(List.of(this)).split("\\s+"));
        int first = words.size();
        while (first > 0 && String.join(" ", words.subList(first - 1, words.size())).length() <= characters) {
            first--;
        }
        return String.join(" ", words.subList(first, words.size()));
    }

    /** The blocks one after another as written, without the whitespace around them. */
    public static String text(List<Block> blocks) {
        return String.join("\n", blocks.stream().flatMap(block -> block.lines().stream()).toList()).strip();
    }
}
