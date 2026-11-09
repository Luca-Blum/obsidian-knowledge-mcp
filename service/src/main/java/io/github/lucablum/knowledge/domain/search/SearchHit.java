package io.github.lucablum.knowledge.domain.search;

import io.github.lucablum.knowledge.domain.note.NoteKind;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A Chunk returned for one query; the score only means something relative to that query. It is the cosine
 * similarity between the query and the Chunk, from -1 to 1. {@code moreInNote} counts the further Chunks of the
 * same Note among the query's candidates that are not among the Search Hits.
 */
@ValueObject
public record SearchHit(
        String content, String notePath, List<String> headingPath, NoteKind noteKind, double score, int moreInNote) {

    public SearchHit {
        headingPath = List.copyOf(headingPath);
    }

    public SearchHit withMoreInNote(int moreInNote) {
        return new SearchHit(content, notePath, headingPath, noteKind, score, moreInNote);
    }
}
