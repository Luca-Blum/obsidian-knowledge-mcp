package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.SourceNoteListing;
import io.github.lucablum.knowledge.domain.Topic;
import io.github.lucablum.knowledge.domain.TopicCount;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** What the Collection holds, so Claude can choose where to search. */
@PrimaryPort
public interface BrowseCollection {

    /** Every Topic with its number of Notes, by name; Notes in the Vault root have no Topic and are not counted. */
    List<TopicCount> topics();

    /** The Source Notes of {@code topic}, by Title. */
    List<SourceNoteListing> sourceNotes(Topic topic);
}
