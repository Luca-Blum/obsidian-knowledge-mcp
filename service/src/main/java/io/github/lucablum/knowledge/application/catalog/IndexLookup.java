package io.github.lucablum.knowledge.application.catalog;

import io.github.lucablum.knowledge.domain.catalog.SourceNoteListing;
import io.github.lucablum.knowledge.domain.catalog.TopicCount;
import io.github.lucablum.knowledge.domain.note.Topic;
import java.util.List;
import org.jmolecules.architecture.hexagonal.Application;

/** Answers from what the index holds, never from the Vault. */
@Application
public class IndexLookup implements BrowseCollection {

    private final Catalog catalog;

    public IndexLookup(Catalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public List<TopicCount> topics() {
        return catalog.topics();
    }

    @Override
    public List<SourceNoteListing> sourceNotes(Topic topic) {
        return catalog.sourceNotes(topic);
    }
}
