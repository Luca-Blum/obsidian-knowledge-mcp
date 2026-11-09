package io.github.lucablum.knowledge.application.link;

import io.github.lucablum.knowledge.domain.link.Related;
import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Lets Claude follow the Links between Notes in both directions. */
@PrimaryPort
public interface FollowLinks {

    /** The Links of {@code note} and its Backlinks, empty if the Note is not indexed. */
    Optional<Related> related(NotePath note);
}
