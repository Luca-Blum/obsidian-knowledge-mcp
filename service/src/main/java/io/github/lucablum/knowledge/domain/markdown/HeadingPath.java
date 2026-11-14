package io.github.lucablum.knowledge.domain.markdown;

import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/** The headings above a place in a Note, outermost first. */
@ValueObject
public record HeadingPath(List<String> segments) {

    public HeadingPath {
        segments = List.copyOf(segments);
    }

    @Override
    public String toString() {
        return String.join(" > ", segments);
    }
}
