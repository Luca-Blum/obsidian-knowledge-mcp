package io.github.lucablum.knowledge.application.search;

import io.github.lucablum.knowledge.domain.search.SearchHit;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Search over the Collection's Chunks. */
@PrimaryPort
public interface SearchChunks {

    /**
     * The Chunks closest in meaning to the request's query, as many as it asks for or else the Collection's default.
     *
     * @throws IllegalArgumentException if the query is blank or the number is not between 1 and the Collection's maximum
     * @throws io.github.lucablum.knowledge.application.indexing.ReIndexRunning while a Re-Index runs
     */
    List<SearchHit> search(SearchRequest request);
}
