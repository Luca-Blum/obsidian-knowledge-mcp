package io.github.lucablum.knowledge.evaluation;

import io.github.lucablum.knowledge.domain.SearchHit;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * The results of one run over an Evaluation Set. The summary holds only numbers and may be committed; the details
 * repeat the questions and Note paths and stay on the machine that ran them.
 */
record Report(List<QuestionResult> questions, List<NegativeResult> negativeQuestions) {

    Report {
        questions = List.copyOf(questions);
        negativeQuestions = List.copyOf(negativeQuestions);
    }

    /** The share of Questions with at least one expected Note in the top 5. */
    double hitAt5() {
        return mean(result -> result.hit() ? 1 : 0);
    }

    /** The mean share of expected Notes in the top 5. */
    double recallAt5() {
        return mean(QuestionResult::recall);
    }

    double meanReciprocalRank() {
        return mean(QuestionResult::reciprocalRank);
    }

    /** The similarity a Minimum Similarity would have to exceed so that no Negative Question gets a hit. */
    double highestNegativeSimilarity() {
        return negativeQuestions.stream()
                .flatMap(result -> result.hits().stream())
                .mapToDouble(SearchHit::score)
                .max()
                .orElse(Double.NaN);
    }

    String summary(Run run) {
        return """
                # Evaluation of Collection %s

                Embedding model `%s`, %d Notes indexed, measured on %s.

                | Metric | Value |
                | --- | --- |
                | Hit@5 | %s |
                | Recall@5 | %s |
                | MRR | %s |
                | Questions | %d |
                | Negative Questions | %d |
                | Highest similarity of a Negative Question | %s |
                """.formatted(run.collection(), run.embeddingModel(), run.notes(), run.date(),
                twoDecimals(hitAt5()), twoDecimals(recallAt5()), twoDecimals(meanReciprocalRank()),
                questions.size(), negativeQuestions.size(), similarity(highestNegativeSimilarity()));
    }

    String details(Run run) {
        StringBuilder details = new StringBuilder(summary(run)).append("\n## Questions\n");
        for (QuestionResult result : questions) {
            int rank = result.firstExpectedRank();
            details.append("\n### ").append(result.question().text()).append("\n\n")
                    .append(rank == 0 ? "Expected Note not in the top 5" : "First expected Note at rank " + rank)
                    .append(", recall ").append(twoDecimals(result.recall())).append(".\n\n");
            appendHits(details, result.top());
        }
        details.append("\n## Negative Questions\n");
        for (NegativeResult result : negativeQuestions) {
            details.append("\n### ").append(result.question().text()).append("\n\n");
            appendHits(details, result.hits());
        }
        return details.toString();
    }

    private static void appendHits(StringBuilder details, List<SearchHit> hits) {
        if (hits.isEmpty()) {
            details.append("No hits.\n");
        }
        for (SearchHit hit : hits) {
            details.append("- ").append(similarity(hit.score())).append(' ').append(hit.notePath()).append('\n');
        }
    }

    private double mean(ToDoubleFunction<QuestionResult> metric) {
        return questions.stream().mapToDouble(metric).average().orElse(Double.NaN);
    }

    private static String twoDecimals(double value) {
        return Double.isNaN(value) ? "–" : String.format(Locale.ROOT, "%.2f", value);
    }

    /** Similarities get three decimals, because a Minimum Similarity is calibrated more finely than a metric. */
    private static String similarity(double value) {
        return Double.isNaN(value) ? "–" : String.format(Locale.ROOT, "%.3f", value);
    }
}
