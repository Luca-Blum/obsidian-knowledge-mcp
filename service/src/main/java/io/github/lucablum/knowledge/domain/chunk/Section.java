package io.github.lucablum.knowledge.domain.chunk;

import java.util.List;
import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;

/** The blocks below one heading, up to the next heading that starts a section; text before any heading has none. */
@ValueObject
record Section(HeadingPath headings, Optional<HeadingLine> heading, List<Block> blocks) {

    Section {
        blocks = List.copyOf(blocks);
    }

    String body() {
        return Block.text(blocks);
    }
}
