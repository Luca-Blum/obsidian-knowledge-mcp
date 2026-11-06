package io.github.lucablum.knowledge.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits a Note into one Chunk per heading section. Frontmatter is not part of any Chunk. */
public class Splitter {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)(?:\\s+#+)?\\s*$");
    private static final Pattern FRONTMATTER_FENCE = Pattern.compile("^---[ \\t]*$", Pattern.MULTILINE);
    private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~)");

    private record Heading(int level, String text) {}

    public List<Chunk> split(NotePath note, String markdown) {
        List<Chunk> chunks = new ArrayList<>();
        Deque<Heading> headings = new ArrayDeque<>();
        StringBuilder section = new StringBuilder();
        boolean inFence = false;
        for (String line : withoutFrontmatter(markdown).split("\\R", -1)) {
            if (FENCE.matcher(line).find()) {
                inFence = !inFence;
            }
            Matcher heading = inFence ? null : HEADING.matcher(line);
            if (heading != null && heading.matches()) {
                addChunk(chunks, note, headings, section);
                int level = heading.group(1).length();
                while (!headings.isEmpty() && headings.peekLast().level() >= level) {
                    headings.removeLast();
                }
                headings.addLast(new Heading(level, heading.group(2)));
            } else {
                section.append(line).append('\n');
            }
        }
        addChunk(chunks, note, headings, section);
        return chunks;
    }

    private static void addChunk(List<Chunk> chunks, NotePath note, Deque<Heading> headings, StringBuilder section) {
        String content = section.toString().strip();
        section.setLength(0);
        if (!content.isEmpty()) {
            HeadingPath path = new HeadingPath(headings.stream().map(Heading::text).toList());
            chunks.add(new Chunk(note, chunks.size(), path, content));
        }
    }

    private static String withoutFrontmatter(String markdown) {
        Matcher fence = FRONTMATTER_FENCE.matcher(markdown);
        if (!fence.lookingAt()) {
            return markdown;
        }
        return fence.find() ? markdown.substring(fence.end()) : markdown;
    }
}
