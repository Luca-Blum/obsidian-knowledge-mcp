package io.github.lucablum.knowledge.domain.markdown;

import io.github.lucablum.knowledge.domain.note.Frontmatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a Note's Markdown line by line, telling headings from text. Frontmatter is left out.
 */
public final class MarkdownLines {

    private static final Pattern CODE_FENCE = Pattern.compile("^\\s*(?<fence>`{3,}|~{3,})");

    private MarkdownLines() {
    }

    public static List<MarkdownLine> of(String markdown) {
        Iterator<String> lines = withoutFrontmatter(List.of(markdown.split("\\R", -1))).iterator();
        List<MarkdownLine> result = new ArrayList<>();
        while (lines.hasNext()) {
            String line = lines.next();
            if (isCodeFence(line)) {
                result.add(codeBlock(line, lines));
            } else {
                result.add(headingOrText(line));
            }
        }
        return result;
    }

    /**
     * The code block from its opening fence up to and including the closing fence, which consumes {@code lines};
     * an unclosed block runs to the end. Only a fence of the same character and at least the same length closes it.
     */
    private static CodeBlock codeBlock(String openingFence, Iterator<String> lines) {
        List<String> block = new ArrayList<>();
        block.add(openingFence);
        while (lines.hasNext()) {
            String line = lines.next();
            block.add(line);
            if (closes(openingFence, line)) {
                break;
            }
        }
        return new CodeBlock(block);
    }

    private static MarkdownLine headingOrText(String line) {
        return HeadingLine.parse(line).<MarkdownLine>map(heading -> heading).orElseGet(() -> new TextLine(line));
    }

    private static boolean closes(String openingFence, String line) {
        String opening = fence(openingFence);
        Matcher closing = CODE_FENCE.matcher(line);
        return closing.find()
                && closing.group("fence").charAt(0) == opening.charAt(0)
                && closing.group("fence").length() >= opening.length()
                && line.substring(closing.end()).isBlank();
    }

    private static String fence(String line) {
        Matcher fence = CODE_FENCE.matcher(line);
        fence.find();
        return fence.group("fence");
    }

    private static boolean isCodeFence(String line) {
        return CODE_FENCE.matcher(line).find();
    }

    private static List<String> withoutFrontmatter(List<String> lines) {
        return lines.subList(Frontmatter.bodyStart(lines), lines.size());
    }
}
