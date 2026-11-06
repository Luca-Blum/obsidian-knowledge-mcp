package io.github.lucablum.knowledge.domain;

import java.text.Normalizer;
import java.util.Objects;
import java.util.Optional;

/** Identifies a Note by its path relative to the Vault root, with '/' separators and NFC-normalized. */
public record NotePath(String value) {

    public NotePath {
        Objects.requireNonNull(value);
        value = Normalizer.normalize(value.replace('\\', '/'), Normalizer.Form.NFC);
    }

    /** The first folder below the Vault root; Notes directly in the root have no Topic. */
    public Optional<Topic> topic() {
        int slash = value.indexOf('/');
        return slash < 0 ? Optional.empty() : Optional.of(new Topic(value.substring(0, slash)));
    }

    /** The file name without {@code .md}, which the Title falls back to. */
    public String fileName() {
        String fileName = value.substring(value.lastIndexOf('/') + 1);
        return fileName.endsWith(".md") ? fileName.substring(0, fileName.length() - ".md".length()) : fileName;
    }

    @Override
    public String toString() {
        return value;
    }
}
