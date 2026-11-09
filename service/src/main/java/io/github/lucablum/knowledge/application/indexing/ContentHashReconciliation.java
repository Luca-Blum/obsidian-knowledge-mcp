package io.github.lucablum.knowledge.application.indexing;

import io.github.lucablum.knowledge.application.Embedding;
import io.github.lucablum.knowledge.domain.chunk.Splitter;
import io.github.lucablum.knowledge.domain.note.ContentHash;
import io.github.lucablum.knowledge.domain.note.NoteParser;
import io.github.lucablum.knowledge.domain.note.NotePath;
import io.github.lucablum.knowledge.domain.note.ParsedNote;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.Application;

/**
 * Compares the content hash of every Note in the Vault with the one stored in the index, parses, splits, embeds and
 * stores only the Notes whose hash differs or is missing, then removes the Notes the Vault no longer has. What looks
 * wrong in a Note is logged as a warning when it is indexed, and indexed as it is.
 */
@Application
public class ContentHashReconciliation implements IndexVault {

    private static final Logger LOG = System.getLogger(ContentHashReconciliation.class.getName());

    private final Vault vault;
    private final NoteParser parser;
    private final Splitter splitter;
    private final Embedding embedding;
    private final IndexedNotes indexedNotes;

    public ContentHashReconciliation(
            Vault vault, NoteParser parser, Splitter splitter, Embedding embedding, IndexedNotes indexedNotes) {
        this.vault = vault;
        this.parser = parser;
        this.splitter = splitter;
        this.embedding = embedding;
        this.indexedNotes = indexedNotes;
    }

    /**
     * Each Note is committed on its own. A Note that cannot be read or embedded keeps its previous version and does
     * not hold up the others, so one broken Note never stops the index from following the rest of the Vault.
     */
    @Override
    public synchronized Reconciled run() {
        Map<NotePath, ContentHash> notInVault = new HashMap<>(indexedNotes.contentHashes());
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
                    index(note.path(), text, hash);
                    indexed++;
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Cannot index Note " + note.path(), e);
                failed.add(note.path());
            }
        }
        indexedNotes.remove(notInVault.keySet());
        return new Reconciled(indexed, notInVault.size(), unchanged, failed);
    }

    private void index(NotePath path, String text, ContentHash hash) {
        ParsedNote parsed = parser.parse(path, text);
        parsed.warnings().forEach(warning -> LOG.log(Level.WARNING, "Note {0} {1}", path, warning));
        List<EmbeddedChunk> chunks = splitter.split(path, text).stream()
                .map(chunk -> new EmbeddedChunk(chunk, embedding.embed(chunk.embeddingText())))
                .toList();
        indexedNotes.replace(parsed.note(), hash, chunks, embedding.model());
    }
}
