package io.github.lucablum.knowledge.evaluation;

import io.github.lucablum.knowledge.application.search.SearchRequest;
import io.github.lucablum.knowledge.domain.note.Topic;

/** A question the Collection must not answer; the Topic and the keywords are optional. */
record NegativeQuestion(String text, Topic topic, String keywords) {

    NegativeQuestion(String text, Topic topic) {
        this(text, topic, null);
    }

    SearchRequest request(int k) {
        return Requests.of(text, topic, keywords, k);
    }
}
