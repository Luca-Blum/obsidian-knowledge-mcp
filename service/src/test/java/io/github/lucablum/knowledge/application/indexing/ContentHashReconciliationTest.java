package io.github.lucablum.knowledge.application.indexing;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.application.Embedding;
import io.github.lucablum.knowledge.domain.chunk.Splitter;
import io.github.lucablum.knowledge.domain.note.ContentHash;
import io.github.lucablum.knowledge.domain.note.Note;
import io.github.lucablum.knowledge.domain.note.NoteLanguage;
import io.github.lucablum.knowledge.domain.note.NoteParser;
import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContentHashReconciliationTest {

    private final InMemoryVault vault = new InMemoryVault();
    private final InMemoryIndex index = new InMemoryIndex();
    private final RecordingEmbedding embedding = new RecordingEmbedding();
    private IndexVault indexVault;

    @BeforeEach
    void reconciliation() {
        indexVault = reconciliation(vault, embedding, index);
    }

    private static IndexVault reconciliation(Vault vault, Embedding embedding, IndexedNotes index) {
        return new ContentHashReconciliation(
                vault, new NoteParser(new NoteLanguage("en")), new Splitter(), embedding, index);
    }

    @Test
    void whenIndexVault_givenNewNote_thenIndexItsChunks() {
        vault.put("Running/Intervals.md", "Interval training improves speed");

        Reconciled reconciled = indexVault.run();

        assertThat(reconciled).isEqualTo(new Reconciled(1, 0, 0, List.of()));
        assertThat(index.contentOf("Running/Intervals.md")).containsExactly("Interval training improves speed");
    }

    @Test
    void whenIndexVault_givenUnchangedNote_thenNeitherEmbedNorReplaceIt() {
        vault.put("Running/Intervals.md", "Interval training improves speed");
        indexVault.run();
        embedding.texts.clear();
        index.replaced.clear();

        Reconciled reconciled = indexVault.run();

        assertThat(reconciled).isEqualTo(new Reconciled(0, 0, 1, List.of()));
        assertThat(embedding.texts).isEmpty();
        assertThat(index.replaced).isEmpty();
    }

    @Test
    void whenIndexVault_givenChangedNote_thenIndexOnlyThatNote() {
        vault.put("Running/Intervals.md", "Interval training improves speed");
        vault.put("Running/Tempo.md", "Tempo runs");
        indexVault.run();
        index.replaced.clear();
        vault.put("Running/Tempo.md", "Tempo runs at threshold pace");

        Reconciled reconciled = indexVault.run();

        assertThat(reconciled).isEqualTo(new Reconciled(1, 0, 1, List.of()));
        assertThat(index.replaced).containsExactly(new NotePath("Running/Tempo.md"));
        assertThat(index.contentOf("Running/Tempo.md")).containsExactly("Tempo runs at threshold pace");
    }

    @Test
    void whenIndexVault_givenNoteDeletedFromVault_thenRemoveItFromIndex() {
        vault.put("Running/Intervals.md", "Interval training improves speed");
        vault.put("Running/Tempo.md", "Tempo runs");
        indexVault.run();
        vault.remove("Running/Tempo.md");

        Reconciled reconciled = indexVault.run();

        assertThat(reconciled).isEqualTo(new Reconciled(0, 1, 1, List.of()));
        assertThat(index.paths()).containsExactly(new NotePath("Running/Intervals.md"));
    }

    @Test
    void whenIndexVault_givenRenamedNote_thenRemoveOldPathAndIndexNewOne() {
        vault.put("Running/Intervals.md", "Interval training improves speed");
        indexVault.run();
        vault.remove("Running/Intervals.md");
        vault.put("Running/Speed/Intervals.md", "Interval training improves speed");

        Reconciled reconciled = indexVault.run();

        assertThat(reconciled).isEqualTo(new Reconciled(1, 1, 0, List.of()));
        assertThat(index.paths()).containsExactly(new NotePath("Running/Speed/Intervals.md"));
    }

    @Test
    void whenIndexVault_givenIndexFromEarlierRun_thenIndexNoteChangedSince() {
        vault.put("Running/Tempo.md", "Tempo runs");
        indexVault.run();
        vault.put("Running/Tempo.md", "Tempo runs at threshold pace");
        IndexVault afterRestart = reconciliation(vault, embedding, index);

        Reconciled reconciled = afterRestart.run();

        assertThat(reconciled).isEqualTo(new Reconciled(1, 0, 0, List.of()));
        assertThat(index.contentOf("Running/Tempo.md")).containsExactly("Tempo runs at threshold pace");
    }

    @Test
    void whenIndexVault_givenNoteThatCannotBeEmbedded_thenIndexTheOthersAndStillRemoveDeletedNotes() {
        vault.put("Running/Tempo.md", "Tempo runs");
        indexVault.run();
        vault.remove("Running/Tempo.md");
        vault.put("Running/Broken.md", "unembeddable");
        vault.put("Running/Intervals.md", "Interval training improves speed");

        Reconciled reconciled = indexVault.run();

        assertThat(reconciled).isEqualTo(new Reconciled(1, 1, 0, List.of(new NotePath("Running/Broken.md"))));
        assertThat(index.paths()).containsExactly(new NotePath("Running/Intervals.md"));
    }

    private static final class InMemoryVault implements Vault {

        private final Map<String, String> files = new LinkedHashMap<>();

        void put(String path, String text) {
            files.put(path, text);
        }

        void remove(String path) {
            files.remove(path);
        }

        @Override
        public List<VaultNote> notes() {
            return files.entrySet().stream()
                    .map(file -> new VaultNote(new NotePath(file.getKey()), file::getValue))
                    .toList();
        }
    }

    private static final class InMemoryIndex implements IndexedNotes {

        private final Map<NotePath, ContentHash> hashes = new HashMap<>();
        private final Map<NotePath, List<EmbeddedChunk>> chunks = new HashMap<>();
        private final List<NotePath> replaced = new ArrayList<>();

        List<String> contentOf(String path) {
            return chunks.get(new NotePath(path)).stream().map(embedded -> embedded.chunk().content()).toList();
        }

        Collection<NotePath> paths() {
            return hashes.keySet();
        }

        @Override
        public Map<NotePath, ContentHash> contentHashes() {
            return Map.copyOf(hashes);
        }

        @Override
        public void replace(Note note, ContentHash hash, List<EmbeddedChunk> chunks, String embeddingModel) {
            replaced.add(note.path());
            hashes.put(note.path(), hash);
            this.chunks.put(note.path(), chunks);
        }

        @Override
        public void remove(Collection<NotePath> notes) {
            notes.forEach(note -> {
                hashes.remove(note);
                chunks.remove(note);
            });
        }
    }

    private static final class RecordingEmbedding implements Embedding {

        private final List<String> texts = new ArrayList<>();

        @Override
        public String model() {
            return "recording";
        }

        @Override
        public float[] embed(String text) {
            if (text.contains("unembeddable")) {
                throw new IllegalStateException("Ollama rejected the text");
            }
            texts.add(text);
            return new float[] {1};
        }
    }
}
