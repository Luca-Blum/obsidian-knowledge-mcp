package io.github.lucablum.knowledge.application.search;

import io.github.lucablum.knowledge.domain.note.Topic;
import io.github.lucablum.knowledge.domain.search.SearchHit;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** The vectors of the Collection's indexed Chunks, searched by closeness to a query. */
@SecondaryPort
public interface ChunkVectors {

    /**
     * The {@code limit} Chunks closest to {@code query} whose cosine similarity reaches {@code minimumSimilarity},
     * closest first, and only those within {@code topic} if there is one.
     */
    List<SearchHit> nearest(float[] query, Optional<Topic> topic, double minimumSimilarity, int limit);
}
