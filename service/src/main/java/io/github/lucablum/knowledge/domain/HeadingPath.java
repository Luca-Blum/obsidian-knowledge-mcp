package io.github.lucablum.knowledge.domain;

import java.util.List;

/** The headings above a Chunk, outermost first. */
public record HeadingPath(List<String> segments) {

    public HeadingPath {
        segments = List.copyOf(segments);
    }

    @Override
    public String toString() {
        return String.join(" > ", segments);
    }
}
