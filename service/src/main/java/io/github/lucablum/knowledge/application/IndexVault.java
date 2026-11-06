package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.NotePath;
import io.github.lucablum.knowledge.domain.splitting.Splitter;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Indexes every Note of the Collection's Vault and removes Notes that are no longer there. */
@PrimaryPort
public class IndexVault {

    private final Vault vault;
    private final Splitter splitter;
    private final Embedding embedding;
    private final Index index;

    public IndexVault(Vault vault, Splitter splitter, Embedding embedding, Index index) {
        this.vault = vault;
        this.splitter = splitter;
        this.embedding = embedding;
        this.index = index;
    }

    /** Returns the number of indexed Notes. */
    public int run() {
        List<NotePath> notes = vault.notes();
        for (NotePath note : notes) {
            List<EmbeddedChunk> chunks = splitter.split(note, vault.read(note)).stream()
                    .map(chunk -> new EmbeddedChunk(chunk, embedding.embed(chunk.embeddingText())))
                    .toList();
            index.replace(note, chunks, embedding.model());
        }
        index.retainOnly(notes);
        return notes.size();
    }
}
