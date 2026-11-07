package io.github.lucablum.knowledge.domain;

import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/** A Note as parsed, with what looked wrong in it; the warnings are reported, never corrected. */
@ValueObject
public record ParsedNote(Note note, List<String> warnings) {

    public ParsedNote {
        warnings = List.copyOf(warnings);
    }
}
