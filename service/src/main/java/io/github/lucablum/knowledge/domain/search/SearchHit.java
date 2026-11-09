package io.github.lucablum.knowledge.domain.search;

import io.github.lucablum.knowledge.domain.note.NoteKind;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A Chunk returned for one query; the score only means something relative to that query. It is the cosine
 * similarity between the query and the Chunk, from -1 to 1.
 */
@ValueObject
public record SearchHit(String content, String notePath, List<String> headingPath, NoteKind noteKind, double score) {

    public SearchHit {
        headingPath = List.copyOf(headingPath);
    }
}
