package io.github.lucablum.knowledge.domain.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import io.github.lucablum.knowledge.domain.note.NoteKind;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {

    @Test
    void whenFuse_givenChunkInBothRankings_thenRankItFirstAndAddUpBothReciprocalRanks() {
        ReciprocalRankFusion fusion = new ReciprocalRankFusion(60, 1.0, 1.0);

        List<SearchHit> fused = fusion.fuse(
                List.of(vector("A.md", 0.8), vector("Both.md", 0.7)),
                List.of(fullText("Both.md", 0.3)));

        assertThat(fused).extracting(SearchHit::notePath).containsExactly("Both.md", "A.md");
        assertThat(fused.getFirst().score()).isCloseTo(1.0 / 62 + 1.0 / 61, within(1e-12));
    }

    @Test
    void whenFuse_givenChunkInBothRankings_thenKeepItsSimilarityAndItsFullTextRank() {
        ReciprocalRankFusion fusion = new ReciprocalRankFusion(60, 1.0, 1.0);

        List<SearchHit> fused = fusion.fuse(List.of(vector("Both.md", 0.7)), List.of(fullText("Both.md", 0.3)));

        assertThat(fused).extracting(SearchHit::similarity, SearchHit::fullTextRank)
                .containsExactly(tuple(OptionalDouble.of(0.7), OptionalDouble.of(0.3)));
    }

    @Test
    void whenFuse_givenFullTextWeightedTwice_thenItsTopChunkOutranksTheVectorTopChunk() {
        ReciprocalRankFusion fusion = new ReciprocalRankFusion(60, 1.0, 2.0);

        List<SearchHit> fused = fusion.fuse(List.of(vector("Vector.md", 0.9)), List.of(fullText("FullText.md", 0.1)));

        assertThat(fused).extracting(SearchHit::notePath, SearchHit::score).containsExactly(
                tuple("FullText.md", 2.0 / 61), tuple("Vector.md", 1.0 / 61));
    }

    @Test
    void whenFuse_givenKOfZero_thenScoreEachChunkOneOverItsRank() {
        ReciprocalRankFusion fusion = new ReciprocalRankFusion(0, 1.0, 1.0);

        List<SearchHit> fused = fusion.fuse(List.of(vector("A.md", 0.9), vector("B.md", 0.8)), List.of());

        assertThat(fused).extracting(SearchHit::score).containsExactly(1.0, 0.5);
    }

    @Test
    void whenFuse_givenFullTextWeightOfZero_thenLeaveItsChunksOut() {
        ReciprocalRankFusion fusion = new ReciprocalRankFusion(60, 1.0, 0.0);

        List<SearchHit> fused = fusion.fuse(List.of(vector("Vector.md", 0.9)), List.of(fullText("FullText.md", 0.1)));

        assertThat(fused).extracting(SearchHit::notePath).containsExactly("Vector.md");
    }

    @Test
    void whenCreate_givenNegativeKOrWeight_thenRejectWithIllegalArgument() {
        assertThatThrownBy(() -> new ReciprocalRankFusion(-1, 1.0, 1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReciprocalRankFusion(60, -0.5, 1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReciprocalRankFusion(60, 1.0, -0.5)).isInstanceOf(IllegalArgumentException.class);
    }

    private static SearchHit vector(String notePath, double similarity) {
        return SearchHit.bySimilarity("content", notePath, 0, List.of(), NoteKind.OWN, similarity);
    }

    private static SearchHit fullText(String notePath, double rank) {
        return SearchHit.byFullTextRank("content", notePath, 0, List.of(), NoteKind.OWN, rank);
    }
}
