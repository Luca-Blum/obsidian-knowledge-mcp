package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.NotePath;
import io.github.lucablum.knowledge.domain.splitting.Splitter;
import java.util.List;
import org.jmolecules.architecture.hexagonal.Application;

/** Indexes the Vault by splitting, embedding and storing every Note again, then drops Notes that are gone. */
@Application
public class VaultIndexer implements IndexVault {

    private final Vault vault;
    private final Splitter splitter;
    private final Embedding embedding;
    private final Index index;

    public VaultIndexer(Vault vault, Splitter splitter, Embedding embedding, Index index) {
        this.vault = vault;
        this.splitter = splitter;
        this.embedding = embedding;
        this.index = index;
    }

    @Override
    public int run() {
        List<VaultNote> notes = vault.notes();
        for (VaultNote note : notes) {
            List<EmbeddedChunk> chunks = splitter.split(note.path(), note.text()).stream()
                    .map(chunk -> new EmbeddedChunk(chunk, embedding.embed(chunk.embeddingText())))
                    .toList();
            index.replace(note.path(), chunks, embedding.model());
        }
        index.retainOnly(notes.stream().map(VaultNote::path).toList());
        return notes.size();
    }
}
