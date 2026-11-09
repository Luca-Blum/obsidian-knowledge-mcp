package io.github.lucablum.knowledge.domain.chunk;

import java.util.ArrayList;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A paragraph or a code block of a section, with the blank lines after it; a section is split between blocks, never
 * inside one. Only prose is repeated as overlap: part of a code block or a table would be unreadable on its own.
 */
@ValueObject
record Block(List<String> lines, boolean prose) {

    Block {
        lines = List.copyOf(lines);
    }

    /** A paragraph is prose unless it holds a table row. */
    static Block paragraph(List<String> lines) {
        return new Block(lines, lines.stream().noneMatch(line -> line.strip().startsWith("|")));
    }

    static Block code(List<String> lines) {
        return new Block(lines, false);
    }

    Block plus(String line) {
        List<String> more = new ArrayList<>(lines);
        more.add(line);
        return new Block(more, prose);
    }

    /**
     * At most the last {@code characters} of this block, starting at a whole word; empty if no whole word fits or the
     * block is not prose.
     */
    String ending(int characters) {
        if (!prose) {
            return "";
        }
        String text = text(List.of(this));
        if (text.length() <= characters) {
            return text;
        }
        int start = text.length() - characters;
        while (start < text.length() && !Character.isWhitespace(text.charAt(start - 1))) {
            start++;
        }
        return text.substring(start).strip();
    }

    /** The blocks' lines as written, without surrounding whitespace. */
    static String text(List<Block> blocks) {
        return String.join("\n", blocks.stream().flatMap(block -> block.lines().stream()).toList()).strip();
    }
}
