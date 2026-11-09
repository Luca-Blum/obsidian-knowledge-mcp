package io.github.lucablum.knowledge.domain.note;

import java.text.Normalizer;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/** The first folder below the Vault root that contains a Note. */
@ValueObject
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
