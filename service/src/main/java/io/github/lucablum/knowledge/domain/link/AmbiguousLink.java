package io.github.lucablum.knowledge.domain.link;

import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/** A Link whose target matches more than one Note; all of them are named instead of one being guessed. */
@ValueObject
public record AmbiguousLink(Link link, List<NotePath> candidates) {

    public AmbiguousLink {
        candidates = List.copyOf(candidates);
    }
}
