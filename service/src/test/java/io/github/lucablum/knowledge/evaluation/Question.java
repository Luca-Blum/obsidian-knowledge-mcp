package io.github.lucablum.knowledge.evaluation;

import io.github.lucablum.knowledge.application.SearchRequest;
import io.github.lucablum.knowledge.domain.NotePath;
import io.github.lucablum.knowledge.domain.Topic;
import java.util.Set;

/** A question of the Evaluation Set with the Notes expected to answer it; the Topic is optional. */
record Question(String text, Topic topic, Set<NotePath> expected) {

    Question {
        expected = Set.copyOf(expected);
    }

    SearchRequest request(int k) {
        SearchRequest request = SearchRequest.of(text).withK(k);
        return topic == null ? request : request.inTopic(topic);
    }
}
