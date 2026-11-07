package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.SourceNoteListing;
import io.github.lucablum.knowledge.domain.Topic;
import io.github.lucablum.knowledge.domain.TopicCount;
import java.util.List;
import org.jmolecules.architecture.hexagonal.Application;

/** Answers from what the index holds, never from the Vault. */
@Application
public class IndexLookup implements BrowseCollection {

    private final Index index;

    public IndexLookup(Index index) {
        this.index = index;
    }

    @Override
    public List<TopicCount> topics() {
        return index.topics();
    }

    @Override
    public List<SourceNoteListing> sourceNotes(Topic topic) {
        return index.sourceNotes(topic);
    }
}
