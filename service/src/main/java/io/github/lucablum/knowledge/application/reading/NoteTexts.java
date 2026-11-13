package io.github.lucablum.knowledge.application.reading;

import io.github.lucablum.knowledge.domain.note.NotePath;
import io.github.lucablum.knowledge.domain.reading.NoteText;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** The full raw text of each indexed Note, as it was when the Note was indexed. */
@SecondaryPort
public interface NoteTexts {

    /** The text of the Note indexed under exactly this path, empty if there is none. */
    Optional<NoteText> text(NotePath path);
}
