package io.github.lucablum.knowledge.adapter.postgres;

import io.agroal.api.AgroalDataSource;
import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.EmbeddedChunk;
import io.github.lucablum.knowledge.application.IndexRepository;
import io.github.lucablum.knowledge.domain.Chunk;
import io.github.lucablum.knowledge.domain.NotePath;
import io.github.lucablum.knowledge.domain.SearchHit;
import io.github.lucablum.knowledge.domain.Topic;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Array;
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
    private final String deleteNoteSql;
    private final String insertNoteSql;
    private final String insertChunkSql;
    private final String retainOnlySql;
    private final String nearestSql;
    private final String nearestInTopicSql;

    public PostgresIndexRepository(AgroalDataSource dataSource, CollectionConfig collection) {
        String schema = collection.schema();
        if (!IDENTIFIER.matcher(schema).matches()) {
            throw new IllegalArgumentException("Invalid schema name: " + schema);
        }
        this.dataSource = dataSource;
        String noteTable = schema + ".note";
        String chunkTable = schema + ".chunk";
        this.deleteNoteSql = "DELETE FROM " + noteTable + " WHERE path = ?";
        this.insertNoteSql = "INSERT INTO " + noteTable + " (path, topic) VALUES (?, ?)";
        this.insertChunkSql = "INSERT INTO " + chunkTable
                + " (note_path, ordinal, heading_path, content, embedding, embedding_model)"
                + " VALUES (?, ?, ?, ?, ?::vector, ?)";
        this.retainOnlySql = "DELETE FROM " + noteTable + " WHERE NOT (path = ANY (?))";
        String nearest = "SELECT c.content, c.note_path, c.heading_path, 1 - (c.embedding <=> ?::vector) AS score"
                + " FROM " + chunkTable + " c JOIN " + noteTable + " n ON n.path = c.note_path";
        String closestFirst = " ORDER BY c.embedding <=> ?::vector LIMIT ?";
        this.nearestSql = nearest + closestFirst;
        this.nearestInTopicSql = nearest + " WHERE n.topic = ?" + closestFirst;
    }

    @Override
    public void replace(NotePath path, List<EmbeddedChunk> chunks, String embeddingModel) {
        inTransaction(connection -> {
            deleteNote(connection, path);
            insertNote(connection, path);
            insertChunks(connection, path, chunks, embeddingModel);
        });
    }

    @Override
    public void retainOnly(Collection<NotePath> notes) {
        inTransaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement(retainOnlySql)) {
                delete.setArray(1, textArray(connection, notes.stream().map(NotePath::value).toList()));
                delete.executeUpdate();
            }
        });
    }

    @Override
    public List<SearchHit> nearest(float[] query, int k) {
        return nearestChunks(query, null, k);
    }

    @Override
    public List<SearchHit> nearest(float[] query, Topic topic, int k) {
        return nearestChunks(query, topic.name(), k);
    }

    private void deleteNote(Connection connection, NotePath path) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(deleteNoteSql)) {
            delete.setString(1, path.value());
            delete.executeUpdate();
        }
    }

    private void insertNote(Connection connection, NotePath path) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(insertNoteSql)) {
            insert.setString(1, path.value());
            insert.setString(2, path.topic().map(Topic::name).orElse(null));
            insert.executeUpdate();
        }
    }

    private void insertChunks(Connection connection, NotePath path, List<EmbeddedChunk> chunks, String embeddingModel)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(insertChunkSql)) {
            insert.setString(1, path.value());
            insert.setString(6, embeddingModel);
            for (EmbeddedChunk embedded : chunks) {
                Chunk chunk = embedded.chunk();
                insert.setInt(2, chunk.ordinal());
                insert.setArray(3, textArray(connection, chunk.headings().segments()));
                insert.setString(4, chunk.content());
                insert.setString(5, Arrays.toString(embedded.embedding()));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    /** {@code topicName} is {@code null} for a search across the whole Collection. */
    private List<SearchHit> nearestChunks(float[] query, String topicName, int k) {
        boolean withinTopic = topicName != null;
        String vector = Arrays.toString(query);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement select = connection.prepareStatement(withinTopic ? nearestInTopicSql : nearestSql)) {
            int parameter = 1;
            select.setString(parameter++, vector);
            if (withinTopic) {
                select.setString(parameter++, topicName);
            }
            select.setString(parameter++, vector);
            select.setInt(parameter, k);
            return readHits(select);
        } catch (SQLException e) {
            throw new IllegalStateException("Search failed", e);
        }
    }

    private static List<SearchHit> readHits(PreparedStatement select) throws SQLException {
        List<SearchHit> hits = new ArrayList<>();
        try (ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                hits.add(toSearchHit(rows));
            }
        }
        return hits;
    }

    private static SearchHit toSearchHit(ResultSet row) throws SQLException {
        return new SearchHit(
                row.getString("content"),
                row.getString("note_path"),
                List.of((String[]) row.getArray("heading_path").getArray()),
                row.getDouble("score"));
    }

    private static Array textArray(Connection connection, Collection<String> values) throws SQLException {
        return connection.createArrayOf("text", values.toArray());
    }

    private void inTransaction(TransactionalWork work) {
        try (Transaction transaction = Transaction.begin(dataSource)) {
            work.run(transaction.connection());
            transaction.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Index update failed", e);
        }
    }

    private interface TransactionalWork {
        void run(Connection connection) throws SQLException;
    }
}
