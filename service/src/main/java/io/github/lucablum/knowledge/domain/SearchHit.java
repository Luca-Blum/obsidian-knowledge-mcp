package io.github.lucablum.knowledge.domain;

import java.util.List;

/** A Chunk returned for one query; the score only means something relative to that query. */
public record SearchHit(String content, String notePath, List<String> headingPath, double score) {

    public SearchHit {
        headingPath = List.copyOf(headingPath);
    }
}
