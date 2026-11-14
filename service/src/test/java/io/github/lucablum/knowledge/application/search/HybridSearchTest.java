package io.github.lucablum.knowledge.application.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.lucablum.knowledge.application.Embedding;
import io.github.lucablum.knowledge.application.indexing.ReIndexCollection;
import io.github.lucablum.knowledge.application.indexing.Reconciled;
import io.github.lucablum.knowledge.application.indexing.ReconciliationProgress;
import io.github.lucablum.knowledge.domain.search.ReciprocalRankFusion;
import io.github.lucablum.knowledge.domain.search.SearchHit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

/** Fusion, the per-Note cap and the request rules, with both rankings laid out by hand. */
class HybridSearchTest {

    private static final int DEFAULT_K = 3;
    private static final int MAX_K = 5;
    private static final double MINIMUM_SIMILARITY = 0.5;
    private static final int MAX_HITS_PER_NOTE = 2;

    private final InMemoryRankings rankings = new InMemoryRankings();
    private final SearchChunks search = new HybridSearch(new AnyEmbedding(), rankings, rankings, new UpToDateIndex(),
            new ReciprocalRankFusion(60, 1.0, 1.0),
            new SearchSettings(DEFAULT_K, MAX_K, MINIMUM_SIMILARITY, MAX_HITS_PER_NOTE, 50));

    @Test
    void whenSearch_givenNoteWithMoreCandidatesThanItsShare_thenReturnAtMostTwoHitsOfIt() {
        rankings.rankedByVector("Running/Long.md", 0, 0.9)
                .rankedByVector("Running/Long.md", 1, 0.85)
                .rankedByVector("Running/Long.md", 2, 0.8)
                .rankedByVector("Running/Short.md", 0, 0.7);

        List<SearchHit> hits = search.search(SearchRequest.of("tempo run").withK(MAX_K));

        assertThat(hits).extracting(SearchHit::notePath)
                .containsExactly("Running/Long.md", "Running/Long.md", "Running/Short.md");
    }

    @Test
    void whenSearch_givenNoteCappedOrCutByK_thenEachHitCountsItsNotesFurtherCandidates() {
        rankings.rankedByVector("Running/Long.md", 0, 0.9)
                .rankedByVector("Running/Long.md", 1, 0.85)
                .rankedByVector("Running/Long.md", 2, 0.8)
                .rankedByVector("Running/Short.md", 0, 0.7);

        List<SearchHit> all = search.search(SearchRequest.of("tempo run").withK(MAX_K));
        List<SearchHit> first = search.search(SearchRequest.of("tempo run").withK(1));

        assertThat(all).extracting(SearchHit::notePath, SearchHit::moreInNote).containsExactly(
                tuple("Running/Long.md", 1), tuple("Running/Long.md", 1), tuple("Running/Short.md", 0));
        assertThat(first).extracting(SearchHit::moreInNote).containsExactly(2);
    }

    @Test
    void whenSearch_givenChunkFoundByBothRankings_thenRankItAboveChunksFoundByOne() {
        rankings.rankedByVector("Running/Tempo.md", 0, 0.9)
                .rankedByVector("Running/Intervals.md", 0, 0.8)
                .rankedByFullText("Running/Intervals.md", 0)
                .rankedByFullText("Running/Hills.md", 0);

        List<SearchHit> hits = search.search(SearchRequest.of("tempo run"));

        assertThat(hits).first().extracting(SearchHit::notePath).isEqualTo("Running/Intervals.md");
    }

    @Test
    void whenSearch_givenVectorCandidatesBelowMinimumSimilarity_thenReturnOnlyTheFullTextMatches() {
        rankings.rankedByVector("Running/Tempo.md", 0, MINIMUM_SIMILARITY - 0.1)
                .rankedByFullText("Running/Hills.md", 0);

        List<SearchHit> hits = search.search(SearchRequest.of("tempo run"));

        assertThat(hits).extracting(SearchHit::notePath).containsExactly("Running/Hills.md");
    }

    @Test
    void whenSearch_givenTopic_thenSearchBothRankingsWithinIt() {
        rankings.rankedByVector("Running/Tempo.md", 0, 0.9)
                .rankedByVector("Cycling/Tempo.md", 0, 0.8)
                .rankedByFullText("Cycling/Hills.md", 0)
                .rankedByFullText("Running/Hills.md", 0);

        List<SearchHit> hits = search.search(SearchRequest.of("tempo").inTopic("Running"));

        assertThat(hits).extracting(SearchHit::notePath)
                .containsExactlyInAnyOrder("Running/Tempo.md", "Running/Hills.md");
    }

    @Test
    void whenSearch_givenNoK_thenReturnTheDefaultNumberOfHits() {
        for (int i = 0; i < DEFAULT_K + 2; i++) {
            rankings.rankedByVector("Running/Tempo " + i + ".md", 0, 0.9 - i * 0.01);
        }

        assertThat(search.search(SearchRequest.of("tempo run"))).hasSize(DEFAULT_K);
    }

    @Test
    void whenSearch_givenBlankQuery_thenRejectWithIllegalArgument() {
        assertThatThrownBy(() -> search.search(SearchRequest.of("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("query must not be blank");
    }

    @Test
    void whenSearch_givenKAboveMaximum_thenRejectWithIllegalArgument() {
        assertThatThrownBy(() -> search.search(SearchRequest.of("tempo run").withK(MAX_K + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("k must be between 1 and " + MAX_K);
    }

    /** The rankings ignore the query vector, so any will do. */
    private static final class AnyEmbedding implements Embedding {

        @Override
        public String model() {
            return "any";
        }

        @Override
        public float[] embed(String text) {
            return new float[] {1};
        }
    }

    private static final class UpToDateIndex implements ReIndexCollection {

        @Override
        public CompletableFuture<Reconciled> reIndex(Executor executor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ReconciliationProgress> progress() {
            return Optional.empty();
        }
    }
}
