package io.github.lucablum.knowledge.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import io.github.lucablum.knowledge.application.search.SearchChunks;
import io.github.lucablum.knowledge.application.search.SearchRequest;
import io.github.lucablum.knowledge.domain.note.NoteKind;
import io.github.lucablum.knowledge.domain.note.NotePath;
import io.github.lucablum.knowledge.domain.note.Topic;
import io.github.lucablum.knowledge.domain.search.SearchHit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvaluationTest {

    private final List<SearchRequest> requests = new ArrayList<>();

    @Test
    void whenRun_givenExpectedNoteAtRankThree_thenCountHitWithReciprocalRankOfOneThird() {
        Evaluation evaluation = new Evaluation(returning(Map.of("dependency rule",
                hits("A.md", "B.md", "Expected.md", "C.md", "D.md"))));

        Report report = evaluation.run(new EvaluationSet(
                List.of(new Question("dependency rule", null, Set.of(new NotePath("Expected.md")))), List.of()));

        assertThat(report.hitAt5()).isEqualTo(1.0);
        assertThat(report.meanReciprocalRank()).isCloseTo(1.0 / 3, within(1e-9));
    }

    @Test
    void whenRun_givenOneNoteFillingTheTopFive_thenCountOtherExpectedNotesAsMissed() {
        Evaluation evaluation = new Evaluation(returning(Map.of("broad question",
                hits("A.md", "A.md", "A.md", "A.md", "A.md", "B.md"))));

        Report report = evaluation.run(new EvaluationSet(List.of(
                new Question("broad question", null, Set.of(new NotePath("A.md"), new NotePath("B.md")))), List.of()));

        assertThat(report.recallAt5()).isEqualTo(0.5);
        assertThat(requests).extracting(request -> request.k().getAsInt()).containsExactly(5);
    }

    @Test
    void whenRun_givenQuestionWithTopic_thenSearchWithinThatTopic() {
        Evaluation evaluation = new Evaluation(returning(Map.of()));

        evaluation.run(new EvaluationSet(List.of(new Question("dependency rule", new Topic("Software-Engineering"),
                Set.of(new NotePath("Software-Engineering/Clean Architecture.md")))), List.of()));

        assertThat(requests).singleElement()
                .satisfies(request -> assertThat(request.topic()).contains(new Topic("Software-Engineering")));
    }

    @Test
    void whenRun_givenNegativeQuestion_thenReportItsHitsWithSimilarity() {
        Evaluation evaluation = new Evaluation(returning(Map.of("marathon taper", List.of(
                new SearchHit("Tapering a release", "Software-Engineering/Releases.md", List.of(), NoteKind.OWN, 0.41, 0),
                new SearchHit("Long runs", "Coaching/Habits.md", List.of(), NoteKind.OWN, 0.38, 0)))));

        Report report = evaluation.run(
                new EvaluationSet(List.of(), List.of(new NegativeQuestion("marathon taper", null))));

        assertThat(report.negativeQuestions()).singleElement().satisfies(result -> {
            assertThat(result.question().text()).isEqualTo("marathon taper");
            assertThat(result.hits()).extracting(SearchHit::notePath, SearchHit::score).containsExactly(
                    tuple("Software-Engineering/Releases.md", 0.41), tuple("Coaching/Habits.md", 0.38));
        });
        assertThat(report.questions()).isEmpty();
    }

    /** A search that answers each query with fixed Search Hits and remembers what it was asked. */
    private SearchChunks returning(Map<String, List<SearchHit>> answers) {
        return request -> {
            requests.add(request);
            return answers.getOrDefault(request.query(), List.of());
        };
    }

    private static List<SearchHit> hits(String... notePaths) {
        List<SearchHit> hits = new ArrayList<>();
        for (int i = 0; i < notePaths.length; i++) {
            hits.add(new SearchHit("content " + i, notePaths[i], List.of(), NoteKind.OWN, 0.9 - i * 0.1, 0));
        }
        return hits;
    }
}
