package io.github.lucablum.knowledge.domain.splitting;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a Note's Markdown line by line, telling headings from text. Frontmatter is left out.
 */
final class MarkdownLines {

    private static final Pattern HEADING = Pattern.compile("(?<marks>#{1,6})\\s+(?<text>(?:\\S.*)?)");
    private static final Pattern CODE_FENCE = Pattern.compile("^\\s*(```|~~~)");
    private static final Pattern FRONTMATTER_FENCE = Pattern.compile("---[ \\t]*");

    private boolean insideCodeFence;

    private MarkdownLines() {
    }

    static List<MarkdownLine> of(String markdown) {
        MarkdownLines reader = new MarkdownLines();
        return withoutFrontmatter(List.of(markdown.split("\\R", -1))).stream().map(reader::classify).toList();
    }

    /**
     * The text of a heading without trailing whitespace and without closing hashes ({@code ## Title ##}).
     */
    private static String headingText(String text) {
        String stripped = text.stripTrailing();
        int end = stripped.length();
        while (end > 0 && stripped.charAt(end - 1) == '#') {
            end--;
        }
        boolean closingHashes = end < stripped.length() && end > 0 && Character.isWhitespace(stripped.charAt(end - 1));
        return closingHashes ? stripped.substring(0, end).stripTrailing() : stripped;
    }

    /**
     * Frontmatter is the block between a first line and the next line of {@code ---}; unclosed, it is just text.
     */
    private static List<String> withoutFrontmatter(List<String> lines) {
        if (!isFrontmatterFence(lines.getFirst())) {
            return lines;
        }
        for (int i = 1; i < lines.size(); i++) {
            if (isFrontmatterFence(lines.get(i))) {
                return lines.subList(i + 1, lines.size());
            }
        }
        return lines;
    }

    private static boolean isFrontmatterFence(String line) {
        return FRONTMATTER_FENCE.matcher(line).matches();
    }

    /**
     * A code fence line opens or closes a code block; inside one, nothing counts as a heading.
     */
    private MarkdownLine classify(String line) {
        if (CODE_FENCE.matcher(line).find()) {
            insideCodeFence = !insideCodeFence;
            return new TextLine(line);
        }
        if (insideCodeFence) {
            return new TextLine(line);
        }
        Matcher heading = HEADING.matcher(line);
        if (heading.matches()) {
            return new HeadingLine(heading.group("marks").length(), headingText(heading.group("text")));
        }
        return new TextLine(line);
    }
}
