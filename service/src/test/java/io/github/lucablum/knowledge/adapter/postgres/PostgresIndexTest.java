package io.github.lucablum.knowledge.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import io.agroal.api.AgroalDataSource;
import io.github.lucablum.knowledge.FakeEmbedding;
import io.github.lucablum.knowledge.TestVault;
import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.indexing.IndexVault;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the index records next to the text: the model of every Chunk and the language of every Note. */
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
}
