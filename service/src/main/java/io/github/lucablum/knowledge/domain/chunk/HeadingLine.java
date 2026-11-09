package io.github.lucablum.knowledge.domain.chunk;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A heading line, as written in {@code line}; down to the configured level it ends the current section and opens the
 * next one, deeper ones stay inside the section.
 */
@ValueObject
record HeadingLine(int level, String text, String line) implements MarkdownLine {

    private static final Pattern HEADING = Pattern.compile("(?<marks>#{1,6})\\s+(?<text>(?:\\S.*)?)");

    /**
     * The heading this line is, or empty if it is not one.
     */
    static Optional<HeadingLine> parse(String line) {
        Matcher heading = HEADING.matcher(line);
        if (!heading.matches()) {
            return Optional.empty();
        }
        return Optional.of(new HeadingLine(heading.group("marks").length(), withoutClosingHashes(heading.group("text")), line));
    }

    /**
     * The text without trailing whitespace and without closing hashes ({@code ## Title ##}).
     */
    private static String withoutClosingHashes(String text) {
        String stripped = text.stripTrailing();
        int end = stripped.length();
        while (end > 0 && stripped.charAt(end - 1) == '#') {
            end--;
        }
        boolean closingHashes = end < stripped.length() && end > 0 && Character.isWhitespace(stripped.charAt(end - 1));
        return closingHashes ? stripped.substring(0, end).stripTrailing() : stripped;
    }
}
