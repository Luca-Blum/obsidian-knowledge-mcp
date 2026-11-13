package io.github.lucablum.knowledge.domain.reading;

import io.github.lucablum.knowledge.domain.chunk.section.HeadingPath;
import java.util.List;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A Note too long to be read in full: the heading path of every heading, in order, so the AI Agent can pick a section,
 * and the beginning of its text.
 */
@ValueObject
public record NoteOutline(List<HeadingPath> headings, String beginning) implements NoteReading {

    public NoteOutline {
        headings = List.copyOf(headings);
        Objects.requireNonNull(beginning);
    }
}
