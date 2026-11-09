package io.github.lucablum.knowledge.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.domain.NotePath;
import io.github.lucablum.knowledge.domain.SearchHit;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReportTest {

    private static final Run RUN = new Run("engineering", "bge-m3", 56, LocalDate.of(2026, 11, 7));

    /** One Question answered at rank 2, one missed, and one Negative Question that still got a hit. */
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
                    List.of(hit("Coaching/Habits.md", 0.43)))));

    @Test
    void whenRenderSummary_givenResults_thenShowRunAndMetricsWithoutQuestionsOrNotePaths() {
        String summary = report.summary(RUN);

        assertThat(summary).contains("engineering", "bge-m3", "56", "2026-11-07",
                "| Hit@5 | 0.50 |", "| Recall@5 | 0.50 |", "| MRR | 0.25 |",
                "| Questions | 2 |", "| Negative Questions | 1 |", "| Highest similarity of a Negative Question | 0.430 |");
        assertThat(summary).doesNotContain("Dependency Rule", "kata", "marathon", ".md");
    }

    @Test
    void whenRenderDetails_givenResults_thenListEveryQuestionWithRankAndEveryNegativeHitWithSimilarity() {
        String details = report.details(RUN);

        assertThat(details).contains(report.summary(RUN),
                "What does the Dependency Rule say?", "rank 2",
                "How do I run a kata?", "not in the top 5",
                "How should I taper before a marathon?", "0.430 Coaching/Habits.md");
    }

    private static SearchHit hit(String notePath, double score) {
        return new SearchHit("content", notePath, List.of(), score);
    }
}
