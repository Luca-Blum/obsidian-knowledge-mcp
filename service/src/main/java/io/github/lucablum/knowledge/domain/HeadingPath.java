package io.github.lucablum.knowledge.domain;

import java.util.List;

/** The headings above a Chunk, outermost first. */
public record HeadingPath(List<String> headings) {

    public HeadingPath {
        headings = List.copyOf(headings);
    }

    @Override
    public String toString() {
        return String.join(" > ", headings);
    }
}
