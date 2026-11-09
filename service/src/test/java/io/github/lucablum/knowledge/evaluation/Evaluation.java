package io.github.lucablum.knowledge.evaluation;

import io.github.lucablum.knowledge.application.SearchChunks;

/** Asks the search every question of an Evaluation Set and judges what it returns. */
final class Evaluation {

    /** Hit@5, Recall@5 and MRR look at the top 5, the number of Search Hits Claude gets by default. */
    static final int TOP = 5;

    private final SearchChunks search;

    Evaluation(SearchChunks search) {
        this.search = search;
    }

    Report run(EvaluationSet set) {
        return new Report(
                set.questions().stream()
                        .map(question -> new QuestionResult(question, search.search(question.request(TOP))))
                        .toList(),
                set.negativeQuestions().stream()
                        .map(question -> new NegativeResult(question, search.search(question.request(TOP))))
                        .toList());
    }
}
