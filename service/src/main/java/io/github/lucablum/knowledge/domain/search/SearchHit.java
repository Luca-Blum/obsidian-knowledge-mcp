package io.github.lucablum.knowledge.domain.search;

import io.github.lucablum.knowledge.domain.note.NoteKind;
import java.util.List;
import java.util.OptionalDouble;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A Chunk returned for one query; the score only orders the Search Hits of that query. The Chunk is identified by its
 * Note and its ordinal. {@code similarity} is the cosine similarity between the query and the Chunk, from -1 to 1, if
 * the vector search found it, and {@code fullTextRank} its rank by the full-text search, if that matched it.
 * {@code moreInNote} counts the further Chunks of the same Note among the query's candidates that are not among the
 * Search Hits.
 */
@ValueObject
public record SearchHit(
        String content,
        String notePath,
        int ordinal,
        List<String> headingPath,
        NoteKind noteKind,
        double score,
        OptionalDouble similarity,
        OptionalDouble fullTextRank,
        int moreInNote) {

    public SearchHit {
        headingPath = List.copyOf(headingPath);
    }

    /** A candidate of the vector search, scored by its similarity. */
    public static SearchHit bySimilarity(
            String content, String notePath, int ordinal, List<String> headingPath, NoteKind noteKind,
            double similarity) {
        return new SearchHit(content, notePath, ordinal, headingPath, noteKind, similarity,
                OptionalDouble.of(similarity), OptionalDouble.empty(), 0);
    }

    /** A candidate of the full-text search, scored by its rank there. */
    public static SearchHit byFullTextRank(
            String content, String notePath, int ordinal, List<String> headingPath, NoteKind noteKind, double rank) {
        return new SearchHit(content, notePath, ordinal, headingPath, noteKind, rank,
                OptionalDouble.empty(), OptionalDouble.of(rank), 0);
    }

    public boolean isSameChunk(SearchHit other) {
        return notePath.equals(other.notePath) && ordinal == other.ordinal;
    }

    /** This Search Hit, also carrying what {@code sameChunk} learned about the Chunk in the other search. */
    public SearchHit mergedWith(SearchHit sameChunk) {
        if (!isSameChunk(sameChunk)) {
            throw new IllegalArgumentException("not the same Chunk: " + sameChunk.notePath + "#" + sameChunk.ordinal);
        }
        return new SearchHit(content, notePath, ordinal, headingPath, noteKind, score,
                similarity.isPresent() ? similarity : sameChunk.similarity,
                fullTextRank.isPresent() ? fullTextRank : sameChunk.fullTextRank, moreInNote);
    }

    public SearchHit withScore(double score) {
        return new SearchHit(content, notePath, ordinal, headingPath, noteKind, score, similarity, fullTextRank,
                moreInNote);
    }

    public SearchHit withMoreInNote(int moreInNote) {
        return new SearchHit(content, notePath, ordinal, headingPath, noteKind, score, similarity, fullTextRank,
                moreInNote);
    }
}
