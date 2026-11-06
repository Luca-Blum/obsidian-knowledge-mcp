package io.github.lucablum.knowledge.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import io.agroal.api.AgroalDataSource;
import io.github.lucablum.knowledge.FakeEmbedding;
import io.github.lucablum.knowledge.TestVault;
import io.github.lucablum.knowledge.application.IndexVault;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The index can never silently mix models, because every Chunk records the model that produced it. */
@QuarkusTest
class PostgresIndexRepositoryTest {

    @Inject
    TestVault vault;

    @Inject
    IndexVault indexVault;

    @Inject
    AgroalDataSource dataSource;

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
}
