package io.github.lucablum.knowledge.domain.chunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** Collects the lines of a Note into sections, tracking the headings above the current one. */
final class SectionAssembler {

    private final List<Section> sections = new ArrayList<>();
    private final Deque<HeadingLine> headings = new ArrayDeque<>();
    private final List<Block> blocks = new ArrayList<>();
    private final List<String> paragraph = new ArrayList<>();

    /** Ends the current section and opens one below {@code heading}. */
    void startSection(HeadingLine heading) {
        endSection();
        dropHeadingsFrom(heading.level());
        headings.addLast(heading);
    }

    /**
     * A non-blank line after a blank one starts a new paragraph. A blank line belongs to the block before it, so no
     * block is blank; the blank lines that open a section belong to none.
     */
    void addLine(String line) {
        if (line.isBlank() && paragraph.isEmpty()) {
            if (!blocks.isEmpty()) {
                blocks.add(blocks.removeLast().plus(line));
            }
            return;
        }
        if (!line.isBlank() && !paragraph.isEmpty() && paragraph.getLast().isBlank()) {
            endParagraph();
        }
        paragraph.add(line);
    }

    void addCodeBlock(CodeBlock code) {
        endParagraph();
        blocks.add(Block.code(code.lines()));
    }

    /** The sections in order; a section without content is left out. */
    List<Section> finish() {
        endSection();
        return List.copyOf(sections);
    }

    private void endParagraph() {
        if (!paragraph.isEmpty()) {
            blocks.add(Block.paragraph(paragraph));
            paragraph.clear();
        }
    }

    private void endSection() {
        endParagraph();
        Section section = new Section(currentHeadingPath(), Optional.ofNullable(headings.peekLast()), blocks);
        blocks.clear();
        if (!section.body().isEmpty()) {
            sections.add(section);
        }
    }

    /** A heading closes every open heading of the same or a deeper level. */
    private void dropHeadingsFrom(int level) {
        while (!headings.isEmpty() && headings.peekLast().level() >= level) {
            headings.removeLast();
        }
    }

    private HeadingPath currentHeadingPath() {
        return new HeadingPath(headings.stream().map(HeadingLine::text).toList());
    }
}
