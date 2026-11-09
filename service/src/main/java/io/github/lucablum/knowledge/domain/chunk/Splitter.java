package io.github.lucablum.knowledge.domain.chunk;

import io.github.lucablum.knowledge.domain.note.Note;
import java.util.ArrayList;
import java.util.List;
import org.jmolecules.ddd.annotation.Service;

/**
 * Splits a Note into Chunks along its sections down to the configured heading level. A section below the minimum size
 * is merged with a neighbour, as long as the result stays within the maximum; a section above the maximum is split at
 * paragraphs, each piece repeating the end of the one before. Frontmatter is not part of any Chunk.
 */
@Service
public class Splitter {

    private final ChunkingRules rules;

    public Splitter(ChunkingRules rules) {
        this.rules = rules;
    }

    public List<Chunk> split(Note note, String markdown) {
        List<Chunk> chunks = new ArrayList<>();
        for (SectionGroup group : merged(sections(markdown))) {
            for (String content : pieces(group)) {
                chunks.add(new Chunk(note, chunks.size(), group.headings(), content));
            }
        }
        return chunks;
    }

    private List<Section> sections(String markdown) {
        SectionAssembler assembler = new SectionAssembler();
        for (MarkdownLine line : MarkdownLines.of(markdown)) {
            switch (line) {
                case HeadingLine heading when heading.level() > rules.headingLevel() -> assembler.addLine(heading.line());
                case HeadingLine heading -> assembler.startSection(heading);
                case TextLine text -> assembler.addLine(text.value());
                case CodeBlock code -> assembler.addCodeBlock(code);
            }
        }
        return assembler.finish();
    }

    private List<SectionGroup> merged(List<Section> sections) {
        List<SectionGroup> groups = new ArrayList<>();
        SectionGroup current = null;
        for (Section section : sections) {
            if (current != null && shouldMerge(current, section)) {
                current = current.plus(section);
            } else {
                if (current != null) {
                    groups.add(current);
                }
                current = SectionGroup.of(section);
            }
        }
        if (current != null) {
            groups.add(current);
        }
        return groups;
    }

    /**
     * The group's content, split at paragraphs if it is above the maximum. Merging never goes above it, so only a
     * group of one section can be.
     */
    private List<String> pieces(SectionGroup group) {
        String content = group.content();
        if (content.length() <= rules.maximumCharacters()) {
            return List.of(content);
        }
        List<String> pieces = new ArrayList<>();
        String overlap = "";
        List<Block> piece = new ArrayList<>();
        for (Block block : group.sections().getFirst().blocks()) {
            if (!piece.isEmpty() && withOverlap(overlap, plus(piece, block)).length() > rules.maximumCharacters()) {
                pieces.add(withOverlap(overlap, piece));
                overlap = piece.getLast().ending(rules.overlapCharacters());
                piece = new ArrayList<>();
            }
            piece.add(block);
        }
        pieces.add(withOverlap(overlap, piece));
        return pieces;
    }

    private static List<Block> plus(List<Block> blocks, Block block) {
        List<Block> more = new ArrayList<>(blocks);
        more.add(block);
        return more;
    }

    private static String withOverlap(String overlap, List<Block> blocks) {
        return overlap.isEmpty() ? Block.text(blocks) : overlap + "\n\n" + Block.text(blocks);
    }

    private boolean shouldMerge(SectionGroup current, Section next) {
        boolean small = current.content().length() < rules.minimumCharacters()
                || next.body().length() < rules.minimumCharacters();
        return small && current.plus(next).content().length() <= rules.maximumCharacters();
    }
}
