package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.NotePath;
import io.github.lucablum.knowledge.domain.splitting.Splitter;
import java.util.List;

/** Indexes every Note of the Collection's Vault and removes Notes that are no longer there. */
public class IndexVault {

    private final VaultReader vault;
    private final Splitter splitter;
    private final EmbeddingPort embedding;
    private final IndexRepository repository;

    public IndexVault(VaultReader vault, Splitter splitter, EmbeddingPort embedding, IndexRepository repository) {
        this.vault = vault;
        this.splitter = splitter;
        this.embedding = embedding;
        this.repository = repository;
    }

    /** Returns the number of indexed Notes. */
    public int run() {
        List<NotePath> notes = vault.notes();
        for (NotePath note : notes) {
            List<EmbeddedChunk> chunks = splitter.split(note, vault.read(note)).stream()
                    .map(chunk -> new EmbeddedChunk(chunk, embedding.embed(chunk.embeddingText())))
                    .toList();
            repository.replace(note, chunks, embedding.model());
        }
        repository.retainOnly(notes);
        return notes.size();
    }
}
