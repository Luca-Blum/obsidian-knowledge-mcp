package io.github.lucablum.knowledge.application.link;

import io.github.lucablum.knowledge.domain.link.Link;
import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** The Links of the Collection's index, as written. */
@SecondaryPort
public interface IndexedLinks {

    /** Every indexed Note, ordered by path, with its Links in the order they are written; a Note without any too. */
    Map<NotePath, List<Link>> ofEveryNote();
}
