package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.SearchHit;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Search over the Collection's Chunks. */
@PrimaryPort
public interface SearchChunks {

    /**
     * The Chunks closest in meaning to the request's query, as many as it asks for or else the Collection's default.
     *
     * @throws IllegalArgumentException if the query is blank or the number is not between 1 and the Collection's maximum
     */
    List<SearchHit> search(SearchRequest request);
}
