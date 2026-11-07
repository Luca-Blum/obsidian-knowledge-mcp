package io.github.lucablum.knowledge.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;

/** What the index knows about a Note beyond its text, taken from its path and frontmatter. */
@ValueObject
public record Note(
        NotePath path,
        NoteKind kind,
        String title,
        Optional<LocalDate> imported,
        Optional<PublishedDate> published,
        Optional<String> author,
        NoteLanguage language) {

    public Note {
        Objects.requireNonNull(path);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(title);
        Objects.requireNonNull(imported);
        Objects.requireNonNull(published);
        Objects.requireNonNull(author);
        Objects.requireNonNull(language);
    }
}
