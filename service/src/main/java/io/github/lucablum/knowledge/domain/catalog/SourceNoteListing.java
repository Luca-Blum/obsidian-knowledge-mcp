package io.github.lucablum.knowledge.domain.catalog;

import io.github.lucablum.knowledge.domain.note.PublishedDate;
import java.time.LocalDate;
import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;

/** A Source Note as listed for its Topic, before the AI Agent decides whether to search or read it. */
@ValueObject
public record SourceNoteListing(
        String notePath,
        String title,
        Optional<PublishedDate> published,
        Optional<LocalDate> imported,
        Optional<String> author) {

    /** The Published Date, otherwise the Imported Date; never a file time. */
    public Optional<String> date() {
        return published.map(PublishedDate::value).or(() -> imported.map(LocalDate::toString));
    }
}
