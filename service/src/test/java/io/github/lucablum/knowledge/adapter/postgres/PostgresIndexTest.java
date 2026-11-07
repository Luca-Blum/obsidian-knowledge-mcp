package io.github.lucablum.knowledge.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.agroal.api.AgroalDataSource;
import io.github.lucablum.knowledge.FakeEmbedding;
import io.github.lucablum.knowledge.TestVault;
import io.github.lucablum.knowledge.application.EmbeddedChunk;
import io.github.lucablum.knowledge.application.Index;
import io.github.lucablum.knowledge.application.IndexVault;
import io.github.lucablum.knowledge.domain.Chunk;
import io.github.lucablum.knowledge.domain.ContentHash;
import io.github.lucablum.knowledge.domain.HeadingPath;
import io.github.lucablum.knowledge.domain.NotePath;
import io.github.lucablum.knowledge.domain.SearchHit;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The index can never silently mix models, because every Chunk records the model that produced it, and never shows
 * half of a Note, because a Note is replaced as a whole or not at all.
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
    Index index;

    @Inject
    FakeEmbedding embedding;

    @BeforeEach
    void emptyIndex() {
        index.remove(index.contentHashes().keySet());
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
    void whenReplace_givenNote_thenStoreItsContentHash() {
        NotePath note = new NotePath("Running/Tempo.md");

        index.replace(note, ContentHash.of("Tempo runs"), List.of(chunk(note, "Tempo runs")), FakeEmbedding.MODEL);

        assertThat(index.contentHashes()).containsExactly(Map.entry(note, ContentHash.of("Tempo runs")));
    }

    @Test
    void whenReplace_givenChunkThatCannotBeStored_thenKeepPreviousVersionOfNote() {
        NotePath note = new NotePath("Running/Tempo.md");
        index.replace(note, ContentHash.of("Tempo runs"), List.of(chunk(note, "Tempo runs")), FakeEmbedding.MODEL);
        List<EmbeddedChunk> newVersion = List.of(
                chunk(note, "Tempo runs at threshold pace"),
                new EmbeddedChunk(new Chunk(note, 1, new HeadingPath(List.of()), "too few dimensions"), new float[] {1}));

        assertThatThrownBy(() -> index.replace(note, ContentHash.of("new"), newVersion, FakeEmbedding.MODEL))
                .isInstanceOf(IllegalStateException.class);

        assertThat(index.contentHashes()).containsEntry(note, ContentHash.of("Tempo runs"));
        assertThat(index.nearest(embedding.embed("tempo runs"), 10)).extracting(SearchHit::content)
                .containsExactly("Tempo runs");
    }

    private EmbeddedChunk chunk(NotePath note, String content) {
        return new EmbeddedChunk(new Chunk(note, 0, new HeadingPath(List.of()), content), embedding.embed(content));
    }
}
