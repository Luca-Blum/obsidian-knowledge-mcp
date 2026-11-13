package io.github.lucablum.knowledge.domain.reading;

import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/** A Note short enough to be read in full, frontmatter included. */
@ValueObject
public record WholeNote(String text) implements NoteReading {

    public WholeNote {
        Objects.requireNonNull(text);
    }
}
