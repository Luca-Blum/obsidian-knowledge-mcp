package io.github.lucablum.knowledge.evaluation;

import io.github.lucablum.knowledge.application.search.SearchRequest;
import io.github.lucablum.knowledge.domain.note.Topic;

/** Asks for an Evaluation Set's question the way Claude would, with its Topic and keywords where it has them. */
final class Requests {

    private Requests() {
    }

    static SearchRequest of(String text, Topic topic, String keywords, int k) {
        SearchRequest request = SearchRequest.of(text).withK(k);
        if (keywords != null && !keywords.isBlank()) {
            request = request.withKeywords(keywords);
        }
        return topic == null ? request : request.inTopic(topic);
    }
}
