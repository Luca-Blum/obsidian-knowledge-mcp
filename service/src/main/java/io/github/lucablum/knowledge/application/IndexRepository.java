package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.NotePath;
import io.github.lucablum.knowledge.domain.SearchHit;
import io.github.lucablum.knowledge.domain.Topic;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** The Collection's index of Notes and their Chunks. */
public interface IndexRepository {

    /** Replaces the Note and all of its Chunks in one transaction. */
    void replace(NotePath note, List<EmbeddedChunk> chunks, String embeddingModel);

    /** Removes every Note that is not among {@code notes}. */
    void retainOnly(Collection<NotePath> notes);

    /** The {@code k} Chunks whose vectors are closest to {@code query}, optionally within one Topic. */
    List<SearchHit> nearest(float[] query, Optional<Topic> topic, int k);
}
