package io.github.lucablum.knowledge.application.search;

import io.github.lucablum.knowledge.domain.note.Topic;
import io.github.lucablum.knowledge.domain.search.SearchHit;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/**
 * The full-text vectors of the Collection's Chunks, built with each Note Language's stemming and stop words and with
 * Titles and headings weighted above body text.
 */
@SecondaryPort
public interface ChunkFullText {

    /**
     * The {@code limit} Chunks that contain every one of {@code terms}, best ranked first, and only those within
     * {@code topic} if there is one. The terms are parsed once per Note Language in the Collection, and each Chunk is
     * compared only with the terms parsed in its own.
     */
    List<SearchHit> matching(String terms, Optional<Topic> topic, int limit);
}
