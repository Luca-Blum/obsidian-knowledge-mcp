package io.github.lucablum.knowledge.domain;

import java.text.Normalizer;
import java.util.Objects;

/** The first folder below the Vault root that contains a Note. */
public record Topic(String name) {

    public Topic {
        Objects.requireNonNull(name);
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
    }

    @Override
    public String toString() {
        return name;
    }
}
