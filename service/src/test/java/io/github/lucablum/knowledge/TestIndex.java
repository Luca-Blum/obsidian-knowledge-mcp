package io.github.lucablum.knowledge;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** The Collection's index in Postgres, as a test arranges and inspects what no port shows: the model of a Chunk. */
@ApplicationScoped
public class TestIndex {

    private final AgroalDataSource dataSource;

    public TestIndex(AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void markEveryChunkAsEmbeddedBy(String model) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement update =
                        connection.prepareStatement("UPDATE test_collection.chunk SET embedding_model = ?")) {
            update.setString(1, model);
            update.executeUpdate();
        }
    }

    public List<String> embeddingModelOfEveryChunk() throws SQLException {
        List<String> models = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                ResultSet rows = connection.createStatement()
                        .executeQuery("SELECT embedding_model FROM test_collection.chunk")) {
            while (rows.next()) {
                models.add(rows.getString(1));
            }
        }
        return models;
    }
}
