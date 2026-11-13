package io.github.lucablum.knowledge.application.search;

import io.github.lucablum.knowledge.domain.note.Topic;
import io.github.lucablum.knowledge.domain.search.SearchHit;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/**
 * The full-text vectors of the Collection's Chunks, built with each Note Language's stemming and stop words and with
 * Titles and headings weighted above body text.
 */
@SecondaryPort
public interface ChunkFullText {

    /**
     * The {@code limit} Chunks that contain every one of {@code terms}, best ranked first. The terms are parsed once
     * per Note Language in the Collection, and each Chunk is compared only with the terms parsed in its own.
     */
    List<SearchHit> matching(String terms, int limit);

    /** The same as {@link #matching(String, int)}, restricted to the Chunks within {@code topic}. */
    List<SearchHit> matching(String terms, Topic topic, int limit);
}
