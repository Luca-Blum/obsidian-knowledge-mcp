package io.github.lucablum.knowledge.evaluation;

import io.github.lucablum.knowledge.application.SearchRequest;
import io.github.lucablum.knowledge.domain.Topic;

/** A question the Collection must not answer; the Topic is optional. */
record NegativeQuestion(String text, Topic topic) {

    SearchRequest request(int k) {
        SearchRequest request = SearchRequest.of(text).withK(k);
        return topic == null ? request : request.inTopic(topic);
    }
}
