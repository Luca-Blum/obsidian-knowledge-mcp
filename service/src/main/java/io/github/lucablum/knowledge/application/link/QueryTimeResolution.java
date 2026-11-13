package io.github.lucablum.knowledge.application.link;

import io.github.lucablum.knowledge.domain.link.Link;
import io.github.lucablum.knowledge.domain.link.Related;
import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.Application;

/**
 * Resolves the Links as written against the Notes indexed at the moment of the question (ADR 0001), so creating,
 * renaming or deleting a Note changes the answer without any other Note being indexed again.
 */
@Application
public class QueryTimeResolution implements FollowLinks {

    private final IndexedLinks indexedLinks;

    public QueryTimeResolution(IndexedLinks indexedLinks) {
        this.indexedLinks = indexedLinks;
    }

    @Override
    public Optional<Related> related(NotePath note) {
        Map<NotePath, List<Link>> linksOfEveryNote = indexedLinks.ofEveryNote();
        return linksOfEveryNote.containsKey(note) ? Optional.of(Related.of(note, linksOfEveryNote)) : Optional.empty();
    }
}
