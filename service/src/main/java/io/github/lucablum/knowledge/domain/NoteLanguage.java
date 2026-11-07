package io.github.lucablum.knowledge.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jmolecules.ddd.annotation.ValueObject;

/** The language a Note is written in, as a code full-text search has stemming for. */
@ValueObject
public record NoteLanguage(String code) {

    private static final Set<String> KNOWN = Set.of("de", "en");

    public NoteLanguage {
        code = normalize(code);
        if (!KNOWN.contains(code)) {
            throw new IllegalArgumentException("Unknown language: " + code + ", known are " + KNOWN);
        }
    }

    /** Empty if {@code code} is not a known language. */
    public static Optional<NoteLanguage> parse(String code) {
        return KNOWN.contains(normalize(code)) ? Optional.of(new NoteLanguage(code)) : Optional.empty();
    }

    private static String normalize(String code) {
        return Objects.requireNonNull(code).strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return code;
    }
}
