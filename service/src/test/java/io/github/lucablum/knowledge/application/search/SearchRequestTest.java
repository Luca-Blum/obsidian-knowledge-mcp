package io.github.lucablum.knowledge.application.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.domain.note.Topic;
import org.junit.jupiter.api.Test;

class SearchRequestTest {

    @Test
    void whenWithKeywords_givenBlank_thenSearchFullTextForTheQuery() {
        SearchRequest request = SearchRequest.of("interval training").withKeywords("  ");

        assertThat(request.keywords()).isEmpty();
        assertThat(request.fullTextTerms()).isEqualTo("interval training");
    }

    @Test
    void whenWithKeywords_givenNone_thenSearchFullTextForTheQuery() {
        SearchRequest request = SearchRequest.of("interval training").withKeywords(null);

        assertThat(request.fullTextTerms()).isEqualTo("interval training");
    }

    @Test
    void whenWithKeywords_givenTerms_thenSearchFullTextForThem() {
        SearchRequest request = SearchRequest.of("where are embeddings kept").withKeywords("PostgresIndex");

        assertThat(request.fullTextTerms()).isEqualTo("PostgresIndex");
    }

    @Test
    void whenInTopic_givenBlank_thenSearchEveryTopic() {
        assertThat(SearchRequest.of("interval training").inTopic(" ").topic()).isEmpty();
    }

    @Test
    void whenInTopic_givenNone_thenSearchEveryTopic() {
        assertThat(SearchRequest.of("interval training").inTopic(null).topic()).isEmpty();
    }

    @Test
    void whenInTopic_givenName_thenSearchThatTopic() {
        assertThat(SearchRequest.of("interval training").inTopic("Running").topic()).contains(new Topic("Running"));
    }
}
