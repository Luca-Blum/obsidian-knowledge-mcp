package io.github.lucablum.knowledge.application.indexing;

import io.github.lucablum.knowledge.domain.note.Note;
import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.Collection;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** The Notes of the Collection's index and their Chunks, as indexing keeps them in line with the Vault. */
@SecondaryPort
public interface IndexedNotes {

    /** Replaces the Note and all of its Chunks in one transaction. */
    void replace(Note note, List<EmbeddedChunk> chunks, String embeddingModel);

    /** Removes every Note that is not among {@code notes}. */
    void retainOnly(Collection<NotePath> notes);
}
