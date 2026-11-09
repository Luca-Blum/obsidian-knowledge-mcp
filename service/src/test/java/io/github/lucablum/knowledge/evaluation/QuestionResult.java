package io.github.lucablum.knowledge.evaluation;

import io.github.lucablum.knowledge.domain.note.NotePath;
import io.github.lucablum.knowledge.domain.search.SearchHit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the search returned for one Question, judged at Note level on the top 5 Search Hits as returned. A Note that
 * fills several of those slots is counted once and leaves fewer slots for the other expected Notes.
 */
record QuestionResult(Question question, List<SearchHit> hits) {

    QuestionResult {
        hits = List.copyOf(hits);
    }

    List<SearchHit> top() {
        return hits.subList(0, Math.min(Evaluation.TOP, hits.size()));
    }

    /** The 1-based rank of the first Search Hit from an expected Note in the top 5, or 0 if there is none. */
    int firstExpectedRank() {
        List<SearchHit> top = top();
        for (int i = 0; i < top.size(); i++) {
            if (isExpected(top.get(i))) {
                return i + 1;
            }
        }
        return 0;
    }

    boolean found() {
        return firstExpectedRank() > 0;
    }

    /** The share of expected Notes that appear in the top 5. */
    double recall() {
        Set<NotePath> found = top().stream()
                .map(hit -> new NotePath(hit.notePath()))
                .filter(question.expected()::contains)
                .collect(Collectors.toSet());
        return (double) found.size() / question.expected().size();
    }

    double reciprocalRank() {
        int rank = firstExpectedRank();
        return rank == 0 ? 0 : 1.0 / rank;
    }

    private boolean isExpected(SearchHit hit) {
        return question.expected().contains(new NotePath(hit.notePath()));
    }
}
