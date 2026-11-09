package io.github.lucablum.knowledge.domain.chunk;

import io.github.lucablum.knowledge.domain.note.Frontmatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads a Note's Markdown line by line, telling headings from text. Frontmatter is left out.
 */
final class MarkdownLines {

    private static final Pattern CODE_FENCE = Pattern.compile("^\\s*(```|~~~)");

    private MarkdownLines() {
    }

    static List<MarkdownLine> of(String markdown) {
        Iterator<String> lines = withoutFrontmatter(List.of(markdown.split("\\R", -1))).iterator();
        List<MarkdownLine> result = new ArrayList<>();
        while (lines.hasNext()) {
            String line = lines.next();
            if (isCodeFence(line)) {
                result.addAll(codeBlock(line, lines));
            } else {
                result.add(headingOrText(line));
            }
        }
        return result;
    }

    /**
     * The code block from its opening fence up to and including the closing fence, which consumes {@code lines};
     * an unclosed block runs to the end.
     */
    private static List<MarkdownLine> codeBlock(String openingFence, Iterator<String> lines) {
        List<MarkdownLine> block = new ArrayList<>();
        block.add(new TextLine(openingFence));
        while (lines.hasNext()) {
            String line = lines.next();
            block.add(new TextLine(line));
            if (isCodeFence(line)) {
                break;
            }
        }
        return block;
    }

    private static MarkdownLine headingOrText(String line) {
        return HeadingLine.parse(line).<MarkdownLine>map(heading -> heading).orElseGet(() -> new TextLine(line));
    }

    private static boolean isCodeFence(String line) {
        return CODE_FENCE.matcher(line).find();
    }

    private static List<String> withoutFrontmatter(List<String> lines) {
        return lines.subList(Frontmatter.bodyStart(lines), lines.size());
    }
}
