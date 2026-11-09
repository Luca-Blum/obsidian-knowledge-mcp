package io.github.lucablum.knowledge.domain.chunk.section;

import io.github.lucablum.knowledge.domain.chunk.markdown.CodeBlock;
import io.github.lucablum.knowledge.domain.chunk.markdown.HeadingLine;
import io.github.lucablum.knowledge.domain.chunk.markdown.MarkdownLine;
import io.github.lucablum.knowledge.domain.chunk.markdown.MarkdownLines;
import io.github.lucablum.knowledge.domain.chunk.markdown.TextLine;
import java.util.ArrayList;
import java.util.List;

/** Collects the lines of a Note into sections, tracking the headings above the current one. */
public final class Sections {

    private final List<Section> sections = new ArrayList<>();
    private OpenHeadings headings = OpenHeadings.NONE;
    private final List<Block> blocks = new ArrayList<>();
    private final List<String> paragraph = new ArrayList<>();

    private Sections() {
    }

    /**
     * The sections of a Note in order, each started by a heading down to {@code headingLevel}; deeper headings stay
     * inside their section. A section without content is left out, and so is frontmatter.
     */
    public static List<Section> of(String markdown, int headingLevel) {
        Sections sections = new Sections();
        for (MarkdownLine line : MarkdownLines.of(markdown)) {
            switch (line) {
                case HeadingLine heading when heading.level() > headingLevel -> sections.addLine(heading.line());
                case HeadingLine heading -> sections.startSection(heading);
                case TextLine text -> sections.addLine(text.value());
                case CodeBlock code -> sections.addCodeBlock(code);
            }
        }
        return sections.finish();
    }

    /** Ends the current section and opens one below {@code heading}. */
    private void startSection(HeadingLine heading) {
        endSection();
        headings = headings.open(heading);
    }

    /**
     * A non-blank line after a blank one starts a new paragraph. A blank line belongs to the block before it, so no
     * block is blank; the blank lines that open a section belong to none.
     */
    private void addLine(String line) {
        if (line.isBlank() && paragraph.isEmpty()) {
            if (!blocks.isEmpty()) {
                blocks.add(blocks.removeLast().withBlankLine(line));
            }
            return;
        }
        if (!line.isBlank() && !paragraph.isEmpty() && paragraph.getLast().isBlank()) {
            endParagraph();
        }
        paragraph.add(line);
    }

    private void addCodeBlock(CodeBlock code) {
        endParagraph();
        blocks.add(Block.code(code.lines()));
    }

    private List<Section> finish() {
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
        Section section = new Section(headings.path(), headings.last(), blocks);
        blocks.clear();
        if (!section.body().isEmpty()) {
            sections.add(section);
        }
    }
}
