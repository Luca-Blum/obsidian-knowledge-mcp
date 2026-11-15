package io.github.lucablum.knowledge.evaluation;

import io.github.lucablum.knowledge.application.search.SearchRequest;

/**
 * A question the Collection must not answer, with its kind; the Topic and the keywords are optional, and blank counts
 * as left out.
 */
record NegativeQuestion(String text, NegativeKind kind, String topic, String keywords) {

    NegativeQuestion(String text, NegativeKind kind, String topic) {
        this(text, kind, topic, null);
    }

    SearchRequest request(int k) {
        return SearchRequest.of(text).withKeywords(keywords).inTopic(topic).withK(k);
    }
}
