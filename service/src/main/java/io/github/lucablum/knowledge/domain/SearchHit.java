package io.github.lucablum.knowledge.domain;

import java.util.List;

/**
 * A Chunk returned for one query; the score only means something relative to that query. It is the cosine
 * similarity between the query and the Chunk, from -1 to 1.
 */
public record SearchHit(String content, String notePath, List<String> headingPath, double score) {

    public SearchHit {
        headingPath = List.copyOf(headingPath);
    }
}
