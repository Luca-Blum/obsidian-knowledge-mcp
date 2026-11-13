package io.github.lucablum.knowledge.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agroal.api.AgroalDataSource;
import io.github.lucablum.knowledge.FakeEmbedding;
import io.github.lucablum.knowledge.TestVault;
import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.indexing.EmbeddedChunk;
import io.github.lucablum.knowledge.application.indexing.IndexVault;
import io.github.lucablum.knowledge.application.indexing.IndexedNotes;
import io.github.lucablum.knowledge.application.search.ChunkVectors;
import io.github.lucablum.knowledge.domain.chunk.Chunk;
import io.github.lucablum.knowledge.domain.chunk.section.HeadingPath;
import io.github.lucablum.knowledge.domain.note.ContentHash;
import io.github.lucablum.knowledge.domain.note.Note;
import io.github.lucablum.knowledge.domain.note.NoteKind;
import io.github.lucablum.knowledge.domain.note.NoteLanguage;
import io.github.lucablum.knowledge.domain.note.NotePath;
import io.github.lucablum.knowledge.domain.search.SearchHit;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What the index records next to the text: the model of every Chunk, the language and content hash of every Note.
 * It never shows half of a Note, because a Note is replaced as a whole or not at all.
 */
@QuarkusTest
class PostgresIndexTest {

    @Inject
    TestVault vault;

    @Inject
    IndexVault indexVault;

    @Inject
    AgroalDataSource dataSource;

    @Inject
    CollectionConfig collection;

    @Inject
    IndexedNotes indexedNotes;

    @Inject
    ChunkVectors vectors;

    @Inject
    FakeEmbedding embedding;

    @BeforeEach
    void emptyIndex() {
        indexedNotes.remove(indexedNotes.contentHashes().keySet());
    }

    @Test
    void whenIndexVault_givenNotes_thenEveryChunkRecordsEmbeddingModel() throws SQLException {
        vault.clear();
        vault.write("Running/Intervals.md", """
                ## Short
                Interval training improves speed

                ## Long
                Long runs build endurance
                """);
        indexVault.run();

        List<String> models = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                ResultSet rows = connection.createStatement()
                        .executeQuery("SELECT embedding_model FROM test_collection.chunk")) {
            while (rows.next()) {
                models.add(rows.getString(1));
            }
        }
        assertThat(models).containsExactly(FakeEmbedding.MODEL, FakeEmbedding.MODEL);
    }

    @Test
    void whenIndexVault_givenLangOrNone_thenStoreNoteLanguageOrCollectionDefault() throws SQLException {
        vault.clear();
        vault.write("Running/German.md", "---\nlang: DE\n---\nIntervalltraining");
        vault.write("Running/Default.md", "Interval training");
        vault.write("Running/Unknown.md", "---\nlang: klingon\n---\nInterval training");
        indexVault.run();

        List<String> languages = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                ResultSet rows = connection.createStatement()
                        .executeQuery("SELECT path || '=' || language FROM test_collection.note ORDER BY path")) {
            while (rows.next()) {
                languages.add(rows.getString(1));
            }
        }
        assertThat(languages).containsExactly(
                "Running/Default.md=" + collection.defaultLanguage(),
                "Running/German.md=de",
                "Running/Unknown.md=" + collection.defaultLanguage());
    }

    @Test
    void whenReplace_givenNote_thenStoreItsContentHash() {
        Note note = note("Running/Tempo.md");

        indexedNotes.replace(
                note, ContentHash.of("Tempo runs"), List.of(), List.of(chunk(note, "Tempo runs")), FakeEmbedding.MODEL);

        assertThat(indexedNotes.contentHashes())
                .containsExactly(Map.entry(note.path(), ContentHash.of("Tempo runs")));
    }

    @Test
    void whenReplace_givenChunkThatCannotBeStored_thenKeepPreviousVersionOfNote() {
        Note note = note("Running/Tempo.md");
        indexedNotes.replace(
                note, ContentHash.of("Tempo runs"), List.of(), List.of(chunk(note, "Tempo runs")), FakeEmbedding.MODEL);
        List<EmbeddedChunk> newVersion = List.of(
                chunk(note, "Tempo runs at threshold pace"),
                new EmbeddedChunk(
                        new Chunk(note, 1, new HeadingPath(List.of()), "too few dimensions"), new float[] {1}));

        assertThatThrownBy(() -> indexedNotes.replace(
                        note, ContentHash.of("new"), List.of(), newVersion, FakeEmbedding.MODEL))
                .isInstanceOf(IllegalStateException.class);

        assertThat(indexedNotes.contentHashes()).containsEntry(note.path(), ContentHash.of("Tempo runs"));
        assertThat(vectors.nearest(embedding.embed("tempo runs"), -1, 10)).extracting(SearchHit::content)
                .containsExactly("Tempo runs");
    }

    private static Note note(String path) {
        return new Note(new NotePath(path), NoteKind.OWN, path, Optional.empty(), Optional.empty(), Optional.empty(),
                new NoteLanguage("en"));
    }

    private EmbeddedChunk chunk(Note note, String content) {
        return new EmbeddedChunk(new Chunk(note, 0, new HeadingPath(List.of()), content), embedding.embed(content));
    }
}
