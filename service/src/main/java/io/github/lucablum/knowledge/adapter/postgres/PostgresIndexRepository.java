package io.github.lucablum.knowledge.adapter.postgres;

import io.agroal.api.AgroalDataSource;
import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.EmbeddedChunk;
import io.github.lucablum.knowledge.application.IndexRepository;
import io.github.lucablum.knowledge.domain.NotePath;
import io.github.lucablum.knowledge.domain.SearchHit;
import io.github.lucablum.knowledge.domain.Topic;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/** Stores Notes and Chunks in the Collection's own schema; vectors live in pgvector columns. */
@SuppressWarnings({"SqlNoDataSourceInspection", "SqlResolve"})
@ApplicationScoped
public class PostgresIndexRepository implements IndexRepository {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]*");

    private final AgroalDataSource dataSource;
    private final String noteTable;
    private final String chunkTable;

    public PostgresIndexRepository(AgroalDataSource dataSource, CollectionConfig collection) {
        String schema = collection.schema();
        if (!IDENTIFIER.matcher(schema).matches()) {
            throw new IllegalArgumentException("Invalid schema name: " + schema);
        }
        this.dataSource = dataSource;
        this.noteTable = schema + ".note";
        this.chunkTable = schema + ".chunk";
    }

    @Override
    public void replace(NotePath path, List<EmbeddedChunk> chunks, String embeddingModel) {
        inTransaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + noteTable + " WHERE path = ?");
                    PreparedStatement insertNote = connection.prepareStatement(
                            "INSERT INTO " + noteTable + " (path, topic) VALUES (?, ?)");
                    PreparedStatement insertChunk = connection.prepareStatement("INSERT INTO " + chunkTable
                            + " (note_path, ordinal, heading_path, content, embedding, embedding_model)"
                            + " VALUES (?, ?, ?, ?, ?::vector, ?)")) {
                delete.setString(1, path.value());
                delete.executeUpdate();
                insertNote.setString(1, path.value());
                insertNote.setString(2, path.topic().map(Topic::name).orElse(null));
                insertNote.executeUpdate();
                for (EmbeddedChunk embedded : chunks) {
                    insertChunk.setString(1, path.value());
                    insertChunk.setInt(2, embedded.chunk().ordinal());
                    insertChunk.setArray(3, connection.createArrayOf(
                            "text", embedded.chunk().headings().segments().toArray()));
                    insertChunk.setString(4, embedded.chunk().content());
                    insertChunk.setString(5, Arrays.toString(embedded.embedding()));
                    insertChunk.setString(6, embeddingModel);
                    insertChunk.addBatch();
                }
                insertChunk.executeBatch();
            }
        });
    }

    @Override
    public void retainOnly(Collection<NotePath> notes) {
        inTransaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM " + noteTable + " WHERE NOT (path = ANY (?))")) {
                delete.setArray(1, connection.createArrayOf(
                        "text", notes.stream().map(NotePath::value).toArray()));
                delete.executeUpdate();
            }
        });
    }

    @Override
    public List<SearchHit> nearest(float[] query, int k) {
        return select(query, null, k);
    }

    @Override
    public List<SearchHit> nearest(float[] query, Topic topic, int k) {
        return select(query, topic.name(), k);
    }

    /** {@code topicName} is {@code null} for a search across the whole Collection. */
    private List<SearchHit> select(float[] query, String topicName, int k) {
        String sql = "SELECT c.content, c.note_path, c.heading_path, 1 - (c.embedding <=> ?::vector) AS score"
                + " FROM " + chunkTable + " c JOIN " + noteTable + " n ON n.path = c.note_path"
                + (topicName != null ? " WHERE n.topic = ?" : "")
                + " ORDER BY c.embedding <=> ?::vector LIMIT ?";
        String vector = Arrays.toString(query);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement select = connection.prepareStatement(sql)) {
            int parameter = 1;
            select.setString(parameter++, vector);
            if (topicName != null) {
                select.setString(parameter++, topicName);
            }
            select.setString(parameter++, vector);
            select.setInt(parameter, k);
            List<SearchHit> hits = new ArrayList<>();
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    hits.add(new SearchHit(
                            rows.getString("content"),
                            rows.getString("note_path"),
                            List.of((String[]) rows.getArray("heading_path").getArray()),
                            rows.getDouble("score")));
                }
            }
            return hits;
        } catch (SQLException e) {
            throw new IllegalStateException("Search failed", e);
        }
    }

    private interface Work {
        void run(Connection connection) throws SQLException;
    }

    private void inTransaction(Work work) {
        try (Connection connection = dataSource.getConnection()) {
            runAtomically(connection, work);
        } catch (SQLException e) {
            throw new IllegalStateException("Index update failed", e);
        }
    }

    private static void runAtomically(Connection connection, Work work) throws SQLException {
        connection.setAutoCommit(false);
        try {
            work.run(connection);
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }
}
