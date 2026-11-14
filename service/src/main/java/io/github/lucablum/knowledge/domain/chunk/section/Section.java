package io.github.lucablum.knowledge.domain.chunk.section;

import io.github.lucablum.knowledge.domain.markdown.HeadingLine;
import io.github.lucablum.knowledge.domain.markdown.HeadingPath;
import java.util.List;
import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;

/** The blocks below one heading, up to the next heading that starts a section; text before any heading has none. */
@ValueObject
public record Section(HeadingPath headings, Optional<HeadingLine> heading, List<Block> blocks) {

    public Section {
        blocks = List.copyOf(blocks);
    }

    public String body() {
        return Block.text(blocks);
    }
}
