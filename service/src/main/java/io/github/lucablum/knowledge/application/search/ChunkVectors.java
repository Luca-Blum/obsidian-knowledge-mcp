package io.github.lucablum.knowledge.application.search;

import io.github.lucablum.knowledge.domain.note.Topic;
import io.github.lucablum.knowledge.domain.search.SearchHit;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** The vectors of the Collection's indexed Chunks, searched by closeness to a query. */
@SecondaryPort
public interface ChunkVectors {

    /** The {@code k} Chunks whose vectors are closest to {@code query}. */
    List<SearchHit> nearest(float[] query, int k);

    /** The {@code k} Chunks within {@code topic} whose vectors are closest to {@code query}. */
    List<SearchHit> nearest(float[] query, Topic topic, int k);
}
