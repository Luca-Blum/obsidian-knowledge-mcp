package io.github.lucablum.knowledge.application.catalog;

import io.github.lucablum.knowledge.domain.catalog.SourceNoteListing;
import io.github.lucablum.knowledge.domain.catalog.TopicCount;
import io.github.lucablum.knowledge.domain.note.Topic;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** What the Collection holds, so the AI Agent can choose where to search. */
@PrimaryPort
public interface BrowseCollection {

    /** Every Topic with its number of Notes, by name; Notes in the Vault root have no Topic and are not counted. */
    List<TopicCount> topics();

    /** The Source Notes of {@code topic}, by Title. */
    List<SourceNoteListing> sourceNotes(Topic topic);
}
