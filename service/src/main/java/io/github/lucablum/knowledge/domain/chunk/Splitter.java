package io.github.lucablum.knowledge.domain.chunk;

import io.github.lucablum.knowledge.domain.chunk.section.Section;
import io.github.lucablum.knowledge.domain.chunk.section.Sections;
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
        for (SectionGroup group : merged(Sections.of(markdown, rules.headingLevel()))) {
            for (String content : contents(group)) {
                chunks.add(new Chunk(note, chunks.size(), group.headings(), content));
            }
        }
        return chunks;
    }

    /** Each section joins the group before it if one of them is small and they fit together. */
    private List<SectionGroup> merged(List<Section> sections) {
        List<SectionGroup> groups = new ArrayList<>();
        for (Section section : sections) {
            if (!groups.isEmpty() && shouldMerge(groups.getLast(), section)) {
                groups.add(groups.removeLast().plus(section));
            } else {
                groups.add(SectionGroup.of(section));
            }
        }
        return groups;
    }

    private boolean shouldMerge(SectionGroup group, Section next) {
        boolean small = group.content().length() < rules.minimumCharacters()
                || next.body().length() < rules.minimumCharacters();
        return small && group.plus(next).content().length() <= rules.maximumCharacters();
    }

    /** Merging never goes above the maximum, so a group above it is a single section, which is split into pieces. */
    private List<String> contents(SectionGroup group) {
        String content = group.content();
        if (content.length() <= rules.maximumCharacters()) {
            return List.of(content);
        }
        return Pieces.of(group.sections().getFirst().blocks(), rules);
    }
}
