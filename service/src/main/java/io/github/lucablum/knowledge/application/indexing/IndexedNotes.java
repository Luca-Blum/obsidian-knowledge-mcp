package io.github.lucablum.knowledge.application.indexing;

import io.github.lucablum.knowledge.domain.note.ContentHash;
import io.github.lucablum.knowledge.domain.note.Note;
import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** The Notes of the Collection's index and their Chunks, as indexing keeps them in line with the Vault. */
@SecondaryPort
public interface IndexedNotes {

    /** The content hash of every indexed Note, as it was when the Note was indexed. */
    Map<NotePath, ContentHash> contentHashes();

    /**
     * Replaces the Note, its full raw text, its content hash and all of its Chunks in one transaction, so a search or
     * a read sees either the old or the new version of the Note, never a mix.
     */
    void replace(Note note, String text, ContentHash hash, List<EmbeddedChunk> chunks, String embeddingModel);

    /** Removes these Notes and their Chunks. */
    void remove(Collection<NotePath> notes);
}
