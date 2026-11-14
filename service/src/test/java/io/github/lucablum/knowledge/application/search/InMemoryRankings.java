package io.github.lucablum.knowledge.application.search;

import io.github.lucablum.knowledge.domain.note.NoteKind;
import io.github.lucablum.knowledge.domain.note.NotePath;
import io.github.lucablum.knowledge.domain.note.Topic;
import io.github.lucablum.knowledge.domain.search.SearchHit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Both rankings as a test lays them out, best first. It keeps what the ports promise, the Minimum Similarity, the
 * Topic and the limit, and ignores the query: which Chunks match a query is the Postgres index's part.
 */
final class InMemoryRankings implements ChunkVectors, ChunkFullText {

    private final List<SearchHit> byVector = new ArrayList<>();
    private final List<SearchHit> byFullText = new ArrayList<>();

    /** Ranks the Chunk next in the vector search with {@code similarity}, which must not exceed the one before. */
    InMemoryRankings rankedByVector(String notePath, int ordinal, double similarity) {
        if (!byVector.isEmpty() && byVector.getLast().similarity().orElseThrow() < similarity) {
            throw new IllegalArgumentException("the vector search ranks the closest Chunk first");
        }
        byVector.add(SearchHit.bySimilarity(content(notePath, ordinal), notePath, ordinal, List.of(), NoteKind.OWN,
                similarity));
        return this;
    }

    /** Ranks the Chunk next in the full-text search. */
    InMemoryRankings rankedByFullText(String notePath, int ordinal) {
        byFullText.add(SearchHit.byFullTextScore(content(notePath, ordinal), notePath, ordinal, List.of(),
                NoteKind.OWN, 1.0 / (byFullText.size() + 1)));
        return this;
    }

    @Override
    public List<SearchHit> nearest(float[] query, Optional<Topic> topic, double minimumSimilarity, int limit) {
        return byVector.stream()
                .filter(hit -> hit.similarity().orElseThrow() >= minimumSimilarity)
                .filter(within(topic))
                .limit(limit)
                .toList();
    }

    @Override
    public List<SearchHit> matching(String terms, Optional<Topic> topic, int limit) {
        return byFullText.stream()
                .filter(within(topic))
                .limit(limit)
                .toList();
    }

    private static Predicate<SearchHit> within(Optional<Topic> topic) {
        return hit -> topic.isEmpty() || new NotePath(hit.notePath()).topic().equals(topic);
    }

    private static String content(String notePath, int ordinal) {
        return notePath + "#" + ordinal;
    }
}
