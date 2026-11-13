package io.github.lucablum.knowledge.adapter.postgres;

import io.agroal.api.AgroalDataSource;
import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.catalog.Catalog;
import io.github.lucablum.knowledge.application.indexing.EmbeddedChunk;
import io.github.lucablum.knowledge.application.indexing.IndexBuild;
import io.github.lucablum.knowledge.application.indexing.IndexFingerprint;
import io.github.lucablum.knowledge.application.indexing.IndexedNotes;
import io.github.lucablum.knowledge.application.link.IndexedLinks;
import io.github.lucablum.knowledge.application.reading.NoteTexts;
import io.github.lucablum.knowledge.application.search.ChunkFullText;
import io.github.lucablum.knowledge.application.search.ChunkVectors;
import io.github.lucablum.knowledge.domain.catalog.SourceNoteListing;
import io.github.lucablum.knowledge.domain.catalog.TopicCount;
import io.github.lucablum.knowledge.domain.chunk.Chunk;
import io.github.lucablum.knowledge.domain.chunk.ChunkingRules;
import io.github.lucablum.knowledge.domain.link.Link;
import io.github.lucablum.knowledge.domain.link.LinkKind;
import io.github.lucablum.knowledge.domain.note.ContentHash;
import io.github.lucablum.knowledge.domain.note.Note;
import io.github.lucablum.knowledge.domain.note.NoteKind;
import io.github.lucablum.knowledge.domain.note.NotePath;
import io.github.lucablum.knowledge.domain.note.PublishedDate;
import io.github.lucablum.knowledge.domain.note.Topic;
import io.github.lucablum.knowledge.domain.reading.NoteText;
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

/**
 * Stores Notes, their text, Links and Chunks in the Collection's own schema; vectors live in pgvector columns, full
 * text in {@code tsvector} columns built in each Note's language. A table of its own records how the index was built.
 */
@ApplicationScoped
@SecondaryAdapter
public class PostgresIndex
        implements IndexedNotes, IndexBuild, ChunkVectors, ChunkFullText, Catalog, IndexedLinks, NoteTexts {

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
    public void replace(Note note, String text, ContentHash hash, List<Link> links, List<EmbeddedChunk> chunks,
            String embeddingModel) {
        Transaction.run(dataSource, connection -> {
            deleteNote(connection, note.path());
            insertNote(connection, note, text, hash);
            insertLinks(connection, note.path(), links);
            insertChunks(connection, note, chunks, embeddingModel);
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
    public Optional<IndexFingerprint> fingerprint() {
        return query(sql.fingerprint(), select -> {}, PostgresIndex::toFingerprint).stream().findFirst();
    }

    /** One transaction, so a crash leaves either the old index with its fingerprint or an empty one without. */
    @Override
    public void startOver(int dimension) {
        Transaction.run(dataSource, connection -> {
            execute(connection, sql.forgetFingerprint());
            execute(connection, sql.removeChunks());
            execute(connection, sql.resizeEmbedding(dimension));
            execute(connection, sql.markNotesChanged());
        });
    }

    @Override
    public void complete(IndexFingerprint fingerprint) {
        Transaction.run(dataSource, connection -> {
            execute(connection, sql.forgetFingerprint());
            insertFingerprint(connection, fingerprint);
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
        }, searchHit(SearchHit::bySimilarity));
    }

    @Override
    public List<SearchHit> matching(String terms, int limit) {
        return matchingInTopic(terms, null, limit);
    }

    @Override
    public List<SearchHit> matching(String terms, Topic topic, int limit) {
        return matchingInTopic(terms, topic.name(), limit);
    }

    /** A {@code null} topic name leaves the search unrestricted. */
    private List<SearchHit> matchingInTopic(String terms, String topicName, int limit) {
        return query(sql.matching(), select -> {
            select.setString(1, terms);
            select.setString(2, topicName);
            select.setString(3, topicName);
            select.setInt(4, limit);
        }, searchHit(SearchHit::byFullTextScore));
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

    @Override
    public Optional<NoteText> text(NotePath path) {
        return query(sql.noteText(), select -> select.setString(1, path.value()),
                row -> new NoteText(row.getString("text"))).stream().findFirst();
    }

    private static void execute(Connection connection, String statement) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(statement)) {
            update.executeUpdate();
        }
    }

    private void insertFingerprint(Connection connection, IndexFingerprint fingerprint) throws SQLException {
        ChunkingRules chunking = fingerprint.chunking();
        try (PreparedStatement insert = connection.prepareStatement(sql.insertFingerprint())) {
            insert.setString(1, fingerprint.embeddingModel());
            insert.setInt(2, fingerprint.dimension());
            insert.setInt(3, chunking.headingLevel());
            insert.setInt(4, chunking.minimumCharacters());
            insert.setInt(5, chunking.maximumCharacters());
            insert.setInt(6, chunking.overlapCharacters());
            insert.executeUpdate();
        }
    }

    private void deleteNote(Connection connection, NotePath path) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(sql.deleteNote())) {
            delete.setString(1, path.value());
            delete.executeUpdate();
        }
    }

    private void insertNote(Connection connection, Note note, String text, ContentHash hash) throws SQLException {
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
            insert.setString(10, text);
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

    private void insertChunks(Connection connection, Note note, List<EmbeddedChunk> chunks, String embeddingModel)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(sql.insertChunk())) {
            insert.setString(1, note.path().value());
            insert.setString(6, embeddingModel);
            insert.setString(7, note.language().code());
            insert.setString(8, note.title());
            for (EmbeddedChunk embedded : chunks) {
                Chunk chunk = embedded.chunk();
                Array headings = textArray(connection, chunk.headings().segments());
                insert.setInt(2, chunk.ordinal());
                insert.setArray(3, headings);
                insert.setString(4, chunk.content());
                insert.setString(5, Arrays.toString(embedded.embedding()));
                insert.setArray(9, headings);
                insert.setString(10, chunk.content());
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

    /** Reads a candidate row of either search; {@code score} is that search's own. */
    private static Row<SearchHit> searchHit(Candidate candidate) {
        return row -> candidate.of(
                row.getString("content"),
                row.getString("note_path"),
                row.getInt("ordinal"),
                List.of((String[]) row.getArray("heading_path").getArray()),
                NoteKind.ofLabel(row.getString("kind")),
                row.getDouble("score"));
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

    private static IndexFingerprint toFingerprint(ResultSet row) throws SQLException {
        return new IndexFingerprint(row.getString("embedding_model"), row.getInt("dimension"), new ChunkingRules(
                row.getInt("heading_level"), row.getInt("minimum_characters"), row.getInt("maximum_characters"),
                row.getInt("overlap_characters")));
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

    private interface Candidate {
        SearchHit of(String content, String notePath, int ordinal, List<String> headingPath, NoteKind noteKind,
                double score);
    }
}
