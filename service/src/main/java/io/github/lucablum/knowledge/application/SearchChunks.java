package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.SearchHit;
import io.github.lucablum.knowledge.domain.Topic;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Search over the Collection's Chunks. */
@PrimaryPort
public interface SearchChunks {

    /** The {@code k} Chunks closest in meaning to {@code query}. */
    List<SearchHit> search(String query, int k);

    /** The {@code k} Chunks within {@code topic} closest in meaning to {@code query}. */
    List<SearchHit> search(String query, Topic topic, int k);
}
