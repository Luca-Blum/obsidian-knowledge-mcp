package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.NotePath;
import java.util.List;

/** Reads the Collection's Vault. Used on the indexing side only. */
public interface VaultReader {

    /** Every Note in the Vault, after the ignore rules are applied. */
    List<NotePath> notes();

    String read(NotePath note);
}
