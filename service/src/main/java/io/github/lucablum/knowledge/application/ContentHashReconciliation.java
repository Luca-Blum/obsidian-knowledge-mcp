package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.ContentHash;
import io.github.lucablum.knowledge.domain.NotePath;
import io.github.lucablum.knowledge.domain.splitting.Splitter;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
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

    private static final Logger LOG = System.getLogger(ContentHashReconciliation.class.getName());

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

    /**
     * Each Note is committed on its own. A Note that cannot be read or embedded keeps its previous version and does
     * not hold up the others, so one broken Note never stops the index from following the rest of the Vault.
     */
    @Override
    public synchronized Reconciled run() {
        Map<NotePath, ContentHash> notInVault = new HashMap<>(index.contentHashes());
        int indexed = 0;
        int unchanged = 0;
        List<NotePath> failed = new ArrayList<>();
        for (VaultNote note : vault.notes()) {
            ContentHash indexedHash = notInVault.remove(note.path());
            try {
                String text = note.text();
                ContentHash hash = ContentHash.of(text);
                if (hash.equals(indexedHash)) {
                    unchanged++;
                } else {
                    index.replace(note.path(), hash, embed(note.path(), text), embedding.model());
                    indexed++;
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Cannot index Note " + note.path(), e);
                failed.add(note.path());
            }
        }
        index.remove(notInVault.keySet());
        return new Reconciled(indexed, notInVault.size(), unchanged, failed);
    }

    private List<EmbeddedChunk> embed(NotePath path, String text) {
        return splitter.split(path, text).stream()
                .map(chunk -> new EmbeddedChunk(chunk, embedding.embed(chunk.embeddingText())))
                .toList();
    }
}
