package io.github.lucablum.knowledge.domain.reading;

import io.github.lucablum.knowledge.domain.chunk.section.HeadingPath;
import java.util.List;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/** The sections of a Note whose heading has {@code heading} as its path, in order; none if no heading has it. */
@ValueObject
public record NoteSections(HeadingPath heading, List<String> sections) implements NoteReading {

    public NoteSections {
        Objects.requireNonNull(heading);
        sections = List.copyOf(sections);
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }
}
