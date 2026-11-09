package io.github.lucablum.knowledge.adapter.postgres;

import io.agroal.api.AgroalDataSource;
import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.catalog.Catalog;
import io.github.lucablum.knowledge.application.indexing.EmbeddedChunk;
import io.github.lucablum.knowledge.application.indexing.IndexedNotes;
import io.github.lucablum.knowledge.application.link.IndexedLinks;
import io.github.lucablum.knowledge.application.search.ChunkVectors;
import io.github.lucablum.knowledge.domain.catalog.SourceNoteListing;
import io.github.lucablum.knowledge.domain.catalog.TopicCount;
import io.github.lucablum.knowledge.domain.chunk.Chunk;
import io.github.lucablum.knowledge.domain.link.Link;
import io.github.lucablum.knowledge.domain.link.LinkKind;
import io.github.lucablum.knowledge.domain.note.ContentHash;
import io.github.lucablum.knowledge.domain.note.Note;
import io.github.lucablum.knowledge.domain.note.NoteKind;
import io.github.lucablum.knowledge.domain.note.NotePath;
import io.github.lucablum.knowledge.domain.note.PublishedDate;
import io.github.lucablum.knowledge.domain.note.Topic;
import io.github.lucablum.knowledge.domain.search.SearchHit;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

/** Stores Notes, their Links and Chunks in the Collection's own schema; vectors live in pgvector columns. */
@ApplicationScoped
@SecondaryAdapter
public class PostgresIndex implements IndexedNotes, ChunkVectors, Catalog, IndexedLinks {

    private final AgroalDataSource dataSource;
    private final Statements sql;

    public PostgresIndex(AgroalDataSource dataSource, CollectionConfig collection) {
        this.dataSource = dataSource;
        this.sql = Statements.forSchema(collection.schema());
    }

    @Override
    public Map<NotePath, ContentHash> contentHashes() {
        return query(sql.contentHashes(), select -> {}, PostgresIndex::toContentHash).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void replace(Note note, ContentHash hash, List<Link> links, List<EmbeddedChunk> chunks,
            String embeddingModel) {
        Transaction.run(dataSource, connection -> {
            deleteNote(connection, note.path());
            insertNote(connection, note, hash);
            insertLinks(connection, note.path(), links);
            insertChunks(connection, note.path(), chunks, embeddingModel);
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
    public List<SearchHit> nearest(float[] query, double minimumSimilarity, int limit) {
        return nearestInTopic(query, null, minimumSimilarity, limit);
    }

    @Override
    public List<SearchHit> nearest(float[] query, Topic topic, double minimumSimilarity, int limit) {
        return nearestInTopic(query, topic.name(), minimumSimilarity, limit);
    }

    /** A {@code null} topic name leaves the search unrestricted. */
    private List<SearchHit> nearestInTopic(float[] query, String topicName, double minimumSimilarity, int limit) {
        String vector = Arrays.toString(query);
        return query(sql.nearest(), select -> {
            select.setString(1, vector);
            select.setString(2, topicName);
            select.setString(3, topicName);
            select.setString(4, vector);
            select.setDouble(5, minimumSimilarity);
            select.setString(6, vector);
            select.setInt(7, limit);
        }, PostgresIndex::toSearchHit);
    }

    @Override
    public List<TopicCount> topics() {
        return query(sql.topics(), select -> {},
                row -> new TopicCount(new Topic(row.getString("topic")), row.getInt("notes")));
    }

    @Override
    public List<SourceNoteListing> sourceNotes(Topic topic) {
        return query(sql.sourceNotes(), select -> select.setString(1, topic.name()), PostgresIndex::toSourceNoteListing);
    }

    @Override
    public Map<NotePath, List<Link>> ofEveryNote() {
        Map<NotePath, List<Link>> linksOfEveryNote = new LinkedHashMap<>();
        List<Map.Entry<NotePath, Optional<Link>>> rows =
                query(sql.linksOfEveryNote(), select -> {}, PostgresIndex::toNoteLink);
        for (Map.Entry<NotePath, Optional<Link>> row : rows) {
            List<Link> links = linksOfEveryNote.computeIfAbsent(row.getKey(), path -> new ArrayList<>());
            row.getValue().ifPresent(links::add);
        }
        return linksOfEveryNote;
    }

    private void deleteNote(Connection connection, NotePath path) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(sql.deleteNote())) {
            delete.setString(1, path.value());
            delete.executeUpdate();
        }
    }

    private void insertNote(Connection connection, Note note, ContentHash hash) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(sql.insertNote())) {
            insert.setString(1, note.path().value());
            insert.setString(2, note.path().topic().map(Topic::name).orElse(null));
            insert.setString(3, note.kind().label());
            insert.setString(4, note.title());
            insert.setObject(5, note.imported().orElse(null), Types.DATE);
            insert.setString(6, note.published().map(PublishedDate::value).orElse(null));
            insert.setString(7, note.author().orElse(null));
            insert.setString(8, note.language().code());
            insert.setString(9, hash.value());
            insert.executeUpdate();
        }
    }

    private void insertLinks(Connection connection, NotePath path, List<Link> links) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(sql.insertLink())) {
            insert.setString(1, path.value());
            for (int ordinal = 0; ordinal < links.size(); ordinal++) {
                Link link = links.get(ordinal);
                insert.setInt(2, ordinal);
                insert.setString(3, link.target());
                insert.setString(4, link.kind().label());
                insert.setString(5, link.anchor().orElse(null));
                insert.addBatch();
            }
            insert.executeBatch();
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

    private <T> List<T> query(String statement, Parameters parameters, Row<T> row) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement select = connection.prepareStatement(statement)) {
            parameters.bindTo(select);
            return readRows(select, row);
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed", e);
        }
    }

    private static <T> List<T> readRows(PreparedStatement select, Row<T> row) throws SQLException {
        List<T> results = new ArrayList<>();
        try (ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                results.add(row.read(rows));
            }
        }
        return results;
    }

    private static SearchHit toSearchHit(ResultSet row) throws SQLException {
        return new SearchHit(
                row.getString("content"),
                row.getString("note_path"),
                List.of((String[]) row.getArray("heading_path").getArray()),
                NoteKind.ofLabel(row.getString("kind")),
                row.getDouble("score"),
                0);
    }

    /** A Note without Links comes as one row without a target. */
    private static Map.Entry<NotePath, Optional<Link>> toNoteLink(ResultSet row) throws SQLException {
        NotePath note = new NotePath(row.getString("path"));
        String target = row.getString("target");
        if (target == null) {
            return Map.entry(note, Optional.empty());
        }
        Link link = new Link(target, LinkKind.ofLabel(row.getString("kind")), Optional.ofNullable(row.getString("anchor")));
        return Map.entry(note, Optional.of(link));
    }

    private static Map.Entry<NotePath, ContentHash> toContentHash(ResultSet row) throws SQLException {
        return Map.entry(new NotePath(row.getString("path")), new ContentHash(row.getString("content_hash")));
    }

    private static SourceNoteListing toSourceNoteListing(ResultSet row) throws SQLException {
        return new SourceNoteListing(
                row.getString("path"),
                row.getString("title"),
                Optional.ofNullable(row.getString("published")).map(PublishedDate::new),
                Optional.ofNullable(row.getObject("imported", LocalDate.class)),
                Optional.ofNullable(row.getString("author")));
    }

    private static Array textArray(Connection connection, Collection<String> values) throws SQLException {
        return connection.createArrayOf("text", values.toArray());
    }

    private interface Parameters {
        void bindTo(PreparedStatement statement) throws SQLException;
    }

    private interface Row<T> {
        T read(ResultSet row) throws SQLException;
    }
}
