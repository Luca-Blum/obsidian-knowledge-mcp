package io.github.lucablum.knowledge.application.catalog;

import io.github.lucablum.knowledge.domain.catalog.SourceNoteListing;
import io.github.lucablum.knowledge.domain.catalog.TopicCount;
import io.github.lucablum.knowledge.domain.note.Topic;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** What the Collection's index holds, by Topic. */
@SecondaryPort
public interface Catalog {

    /** Every Topic with its number of Notes, ordered by name. */
    List<TopicCount> topics();

    /** The Source Notes within {@code topic}, ordered by Title. */
    List<SourceNoteListing> sourceNotes(Topic topic);
}
