package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.ContentHash;
import io.github.lucablum.knowledge.domain.NotePath;
import io.github.lucablum.knowledge.domain.splitting.Splitter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.Application;

/**
 * Compares the content hash of every Note in the Vault with the one stored in the index, splits, embeds and stores
 * only the Notes whose hash differs or is missing, then removes the Notes the Vault no longer has.
 */
@Application
public class ContentHashReconciliation implements IndexVault {

    private final Vault vault;
    private final Splitter splitter;
    private final Embedding embedding;
    private final Index index;

    public ContentHashReconciliation(Vault vault, Splitter splitter, Embedding embedding, Index index) {
        this.vault = vault;
        this.splitter = splitter;
        this.embedding = embedding;
        this.index = index;
    }

    /** Each Note is committed on its own, so a run that fails halfway keeps what it indexed so far. */
    @Override
    public synchronized Reconciled run() {
        Map<NotePath, ContentHash> gone = new HashMap<>(index.contentHashes());
        int indexed = 0;
        int unchanged = 0;
        for (VaultNote note : vault.notes()) {
            String text = note.text();
            ContentHash hash = ContentHash.of(text);
            if (hash.equals(gone.remove(note.path()))) {
                unchanged++;
            } else {
                index.replace(note.path(), hash, embed(note.path(), text), embedding.model());
                indexed++;
            }
        }
        index.remove(gone.keySet());
        return new Reconciled(indexed, gone.size(), unchanged);
    }

    private List<EmbeddedChunk> embed(NotePath path, String text) {
        return splitter.split(path, text).stream()
                .map(chunk -> new EmbeddedChunk(chunk, embedding.embed(chunk.embeddingText())))
                .toList();
    }
}
