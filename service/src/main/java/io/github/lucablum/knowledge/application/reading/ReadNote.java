package io.github.lucablum.knowledge.application.reading;

import io.github.lucablum.knowledge.domain.markdown.HeadingPath;
import io.github.lucablum.knowledge.domain.note.NotePath;
import io.github.lucablum.knowledge.domain.reading.NoteReading;
import io.github.lucablum.knowledge.domain.reading.NoteSections;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/**
 * Reads a Note after a promising Search Hit. The path must be exactly an indexed Note's; anything that is not indexed
 * does not exist, so both return empty for it.
 */
@PrimaryPort
public interface ReadNote {

    /** The whole Note, or the outline and beginning of a long one. */
    Optional<NoteReading> read(NotePath path);

    /** The Note's sections whose heading has this path, as a Search Hit shows it; the empty path is the whole body. */
    Optional<NoteSections> read(NotePath path, HeadingPath heading);
}
