package io.github.lucablum.knowledge.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.domain.note.NoteKind;
import io.github.lucablum.knowledge.domain.note.NotePath;
import io.github.lucablum.knowledge.domain.search.SearchHit;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReportTest {

    private static final Run RUN = new Run("engineering", "bge-m3", 0.4, new Run.Fusion(60, 1.0, 0.5), 56,
            LocalDate.of(2026, 11, 7));

    /**
     * One Question answered at rank 2, one missed, one Negative Question with a hit above the Minimum Similarity of
     * 0.4 and one whose only hit stays below it.
     */
    private final Report report = new Report(
            List.of(
                    new QuestionResult(
                            new Question("What does the Dependency Rule say?", null,
                                    Set.of(new NotePath("Software-Engineering/Clean Architecture.md"))),
                            List.of(hit("Software-Engineering/Other.md", 0.71),
                                    hit("Software-Engineering/Clean Architecture.md", 0.69))),
                    new QuestionResult(
                            new Question("How do I run a kata?", null, Set.of(new NotePath("Coaching/Kata.md"))),
                            List.of(hit("Coaching/Dojo.md", 0.52)))),
            List.of(new NegativeResult(new NegativeQuestion("How should I taper before a marathon?", null),
                            List.of(hit("Coaching/Habits.md", 0.43))),
                    new NegativeResult(new NegativeQuestion("Which bike fits me?", null),
                            List.of(hit("Coaching/Gear.md", 0.38)))));

    @Test
    void whenRenderSummary_givenResults_thenShowRunAndMetricsWithoutQuestionsOrNotePaths() {
        String summary = report.summary(RUN);

        assertThat(summary).contains("engineering", "bge-m3", "56", "2026-11-07",
                "| Hit@5 | 0.50 |", "| Recall@5 | 0.50 |", "| MRR | 0.25 |",
                "| Questions | 2 |", "| Minimum Similarity | 0.400 |", "| Negative Questions passed | 1 of 2 |",
                "| Highest similarity of a Negative Question | 0.430 |",
                "| Fusion | k 60, vector weight 1.00, full-text weight 0.50 |");
        assertThat(summary).doesNotContain("Dependency Rule", "kata", "marathon", "bike", ".md");
    }

    @Test
    void whenRenderDetails_givenResults_thenListEveryQuestionWithRankAndEveryNegativeHitWithSimilarity() {
        String details = report.details(RUN);

        assertThat(details).contains(report.summary(RUN),
                "What does the Dependency Rule say?", "rank 2",
                "How do I run a kata?", "not in the top 5",
                "How should I taper before a marathon?", "0.430 Coaching/Habits.md");
    }

    @Test
    void whenRenderDetails_givenNegativeQuestions_thenMarkEachPassedOrFailedAgainstMinimumSimilarity() {
        String details = report.details(RUN);

        assertThat(details).contains(
                "### How should I taper before a marathon?\n\nFailed: a hit reaches the Minimum Similarity of 0.400.",
                "### Which bike fits me?\n\nPassed: no hit reaches the Minimum Similarity of 0.400.");
    }

    @Test
    void whenRenderSummary_givenNegativeQuestionWithOnlyFullTextHits_thenPassItAndCountItsFullTextHitsApart() {
        Report withFullText = new Report(List.of(), List.of(
                new NegativeResult(new NegativeQuestion("How should I taper before a marathon?", null),
                        List.of(fullTextHit("Coaching/Marathon.md", 0.2).withScore(0.016))),
                new NegativeResult(new NegativeQuestion("Which bike fits me?", null), List.of())));

        String summary = withFullText.summary(RUN);

        assertThat(summary).contains("| Negative Questions passed | 2 of 2 |",
                "| Highest similarity of a Negative Question | – |",
                "| Negative Questions with full-text hits (information only) | 1 of 2 |");
    }

    @Test
    void whenRenderDetails_givenNegativeQuestionWithVectorAndFullTextHits_thenListFullTextHitsSeparately() {
        Report withFullText = new Report(List.of(), List.of(
                new NegativeResult(new NegativeQuestion("How should I taper before a marathon?", null), List.of(
                        hit("Coaching/Habits.md", 0.43),
                        fullTextHit("Coaching/Marathon.md", 0.2)))));

        String details = withFullText.details(RUN);

        assertThat(details).contains("""
                Failed: a hit reaches the Minimum Similarity of 0.400.

                - 0.430 Coaching/Habits.md

                Full-text hits, for information only:

                - Coaching/Marathon.md
                """);
    }

    private static SearchHit hit(String notePath, double score) {
        return SearchHit.bySimilarity("content", notePath, 0, List.of(), NoteKind.OWN, score);
    }

    private static SearchHit fullTextHit(String notePath, double rank) {
        return SearchHit.byFullTextRank("content", notePath, 0, List.of(), NoteKind.OWN, rank);
    }
}
