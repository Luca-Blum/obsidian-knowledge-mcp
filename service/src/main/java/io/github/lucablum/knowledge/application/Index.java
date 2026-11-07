package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.Note;
import io.github.lucablum.knowledge.domain.NotePath;
import io.github.lucablum.knowledge.domain.SearchHit;
import io.github.lucablum.knowledge.domain.SourceListing;
import io.github.lucablum.knowledge.domain.Topic;
import io.github.lucablum.knowledge.domain.TopicCount;
import java.util.Collection;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** The Collection's index of Notes and their Chunks. */
@SecondaryPort
public interface Index {

    /** Replaces the Note and all of its Chunks in one transaction. */
    void replace(Note note, List<EmbeddedChunk> chunks, String embeddingModel);

    /** Removes every Note that is not among {@code notes}. */
    void retainOnly(Collection<NotePath> notes);

    /** The {@code k} Chunks whose vectors are closest to {@code query}. */
    List<SearchHit> nearest(float[] query, int k);

    /** The {@code k} Chunks within {@code topic} whose vectors are closest to {@code query}. */
    List<SearchHit> nearest(float[] query, Topic topic, int k);

    /** Every Topic with its number of Notes, ordered by name. */
    List<TopicCount> topics();

    /** The Source Notes within {@code topic}, ordered by Title. */
    List<SourceListing> sources(Topic topic);
}
