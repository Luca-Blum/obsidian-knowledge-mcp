package io.github.lucablum.knowledge.adapter.postgres;

import io.agroal.api.AgroalDataSource;
import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.EmbeddedChunk;
import io.github.lucablum.knowledge.application.Index;
import io.github.lucablum.knowledge.domain.Chunk;
import io.github.lucablum.knowledge.domain.ContentHash;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

/** Stores Notes and Chunks in the Collection's own schema; vectors live in pgvector columns. */
@ApplicationScoped
@SecondaryAdapter
public class PostgresIndex implements Index {

    private final AgroalDataSource dataSource;
    private final Statements sql;

    public PostgresIndex(AgroalDataSource dataSource, CollectionConfig collection) {
        this.dataSource = dataSource;
        this.sql = Statements.forSchema(collection.schema());
    }

    /** A Note indexed before content hashes were stored has none, so it is left out and indexed again. */
    @Override
    public Map<NotePath, ContentHash> contentHashes() {
        Map<NotePath, ContentHash> hashes = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement select = connection.prepareStatement(sql.contentHashes());
                ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String hash = rows.getString("content_hash");
                if (hash != null) {
                    hashes.put(new NotePath(rows.getString("path")), new ContentHash(hash));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Reading the content hashes failed", e);
        }
        return hashes;
    }

    @Override
    public void replace(NotePath path, ContentHash hash, List<EmbeddedChunk> chunks, String embeddingModel) {
        Transaction.run(dataSource, connection -> {
            deleteNote(connection, path);
            insertNote(connection, path, hash);
            insertChunks(connection, path, chunks, embeddingModel);
        });
    }

    @Override
    public void remove(Collection<NotePath> notes) {
        Transaction.run(dataSource, connection -> {
            try (PreparedStatement delete = connection.prepareStatement(sql.removeNotes())) {
                delete.setArray(1, textArray(connection, notes.stream().map(NotePath::value).toList()));
                delete.executeUpdate();
            }
        });
    }

    @Override
    public List<SearchHit> nearest(float[] query, int k) {
        return nearestInTopic(query, null, k);
    }

    @Override
    public List<SearchHit> nearest(float[] query, Topic topic, int k) {
        return nearestInTopic(query, topic.name(), k);
    }

    /** A {@code null} topic name leaves the search unrestricted. */
    private List<SearchHit> nearestInTopic(float[] query, String topicName, int k) {
        String vector = Arrays.toString(query);
        return search(sql.nearest(), select -> {
            select.setString(1, vector);
            select.setString(2, topicName);
            select.setString(3, topicName);
            select.setString(4, vector);
            select.setInt(5, k);
        });
    }

    private void deleteNote(Connection connection, NotePath path) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(sql.deleteNote())) {
            delete.setString(1, path.value());
            delete.executeUpdate();
        }
    }

    private void insertNote(Connection connection, NotePath path, ContentHash hash) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(sql.insertNote())) {
            insert.setString(1, path.value());
            insert.setString(2, path.topic().map(Topic::name).orElse(null));
            insert.setString(3, hash.value());
            insert.executeUpdate();
        }
    }

    private void insertChunks(Connection connection, NotePath path, List<EmbeddedChunk> chunks, String embeddingModel)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(sql.insertChunk())) {
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

    private List<SearchHit> search(String statement, Parameters parameters) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement select = connection.prepareStatement(statement)) {
            parameters.bindTo(select);
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

    private interface Parameters {
        void bindTo(PreparedStatement statement) throws SQLException;
    }
}
