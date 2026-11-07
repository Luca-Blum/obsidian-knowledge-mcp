package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.ContentHash;
import io.github.lucablum.knowledge.domain.NotePath;
import io.github.lucablum.knowledge.domain.SearchHit;
import io.github.lucablum.knowledge.domain.Topic;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** The Collection's index of Notes and their Chunks. */
@SecondaryPort
public interface Index {

    /** The content hash of every indexed Note, as it was when the Note was indexed. */
    Map<NotePath, ContentHash> contentHashes();

    /**
     * Replaces the Note, its content hash and all of its Chunks in one transaction, so a search sees either the old
     * or the new version of the Note, never a mix.
     */
    void replace(NotePath note, ContentHash hash, List<EmbeddedChunk> chunks, String embeddingModel);

    /** Removes these Notes and their Chunks. */
    void remove(Collection<NotePath> notes);

    /** The {@code k} Chunks whose vectors are closest to {@code query}. */
    List<SearchHit> nearest(float[] query, int k);

    /** The {@code k} Chunks within {@code topic} whose vectors are closest to {@code query}. */
    List<SearchHit> nearest(float[] query, Topic topic, int k);
}
