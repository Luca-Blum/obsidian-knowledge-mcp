package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.NotePath;
import java.util.function.Supplier;
import org.jmolecules.ddd.annotation.ValueObject;

/** A Note found in the Vault, which knows how to read its own text once that is needed. */
@ValueObject
public record VaultNote(NotePath path, Supplier<String> reader) {

    public String text() {
        return reader.get();
    }
}
