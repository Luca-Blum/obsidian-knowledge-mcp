package io.github.lucablum.knowledge.application.indexing;

import io.github.lucablum.knowledge.application.Embedding;
import io.github.lucablum.knowledge.domain.chunk.Splitter;
import io.github.lucablum.knowledge.domain.note.NoteParser;
import io.github.lucablum.knowledge.domain.note.ParsedNote;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import org.jmolecules.architecture.hexagonal.Application;

/**
 * Indexes the Vault by parsing, splitting, embedding and storing every Note again, then drops Notes that are gone.
 * What looks wrong in a Note is logged as a warning and indexed as it is.
 */
@Application
public class VaultIndexer implements IndexVault {

    private static final Logger LOG = System.getLogger(VaultIndexer.class.getName());

    private final Vault vault;
    private final NoteParser parser;
    private final Splitter splitter;
    private final Embedding embedding;
    private final IndexedNotes indexedNotes;

    public VaultIndexer(Vault vault, NoteParser parser, Splitter splitter, Embedding embedding, IndexedNotes indexedNotes) {
        this.vault = vault;
        this.parser = parser;
        this.splitter = splitter;
        this.embedding = embedding;
        this.indexedNotes = indexedNotes;
    }

    @Override
    public int run() {
        List<VaultNote> notes = vault.notes();
        for (VaultNote note : notes) {
            String text = note.text();
            ParsedNote parsed = parser.parse(note.path(), text);
            parsed.warnings().forEach(warning -> LOG.log(Level.WARNING, "Note {0} {1}", note.path(), warning));
            List<EmbeddedChunk> chunks = splitter.split(note.path(), text).stream()
                    .map(chunk -> new EmbeddedChunk(chunk, embedding.embed(chunk.embeddingText())))
                    .toList();
            indexedNotes.replace(parsed.note(), chunks, embedding.model());
        }
        indexedNotes.retainOnly(notes.stream().map(VaultNote::path).toList());
        return notes.size();
    }
}
