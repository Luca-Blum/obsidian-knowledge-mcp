package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.NotePath;
import io.github.lucablum.knowledge.domain.SearchHit;
import io.github.lucablum.knowledge.domain.Topic;
import java.util.Collection;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** The Collection's index of Notes and their Chunks. */
@SecondaryPort
public interface Index {

    /** Replaces the Note and all of its Chunks in one transaction. */
    void replace(NotePath note, List<EmbeddedChunk> chunks, String embeddingModel);

    /** Removes every Note that is not among {@code notes}. */
    void retainOnly(Collection<NotePath> notes);

    /**
     * The {@code limit} Chunks closest to {@code query} whose cosine similarity reaches {@code minimumSimilarity},
     * closest first.
     */
    List<SearchHit> nearest(float[] query, double minimumSimilarity, int limit);

    /** The same as {@link #nearest(float[], double, int)}, restricted to the Chunks within {@code topic}. */
    List<SearchHit> nearest(float[] query, Topic topic, double minimumSimilarity, int limit);
}
