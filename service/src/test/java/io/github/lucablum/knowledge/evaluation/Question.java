package io.github.lucablum.knowledge.evaluation;

import io.github.lucablum.knowledge.application.search.SearchRequest;
import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.Set;

/**
 * A question of the Evaluation Set with its Category and the Notes expected to answer it. The Topic and the keywords
 * for the full-text search are optional; without keywords the full-text search uses the question.
 */
record Question(String text, Category category, String topic, String keywords, Set<NotePath> expected) {

    Question {
        expected = Set.copyOf(expected);
    }

    Question(String text, Category category, String topic, Set<NotePath> expected) {
        this(text, category, topic, null, expected);
    }

    SearchRequest request(int k) {
        return SearchRequest.of(text).withKeywords(keywords).inTopic(topic).withK(k);
    }
}
