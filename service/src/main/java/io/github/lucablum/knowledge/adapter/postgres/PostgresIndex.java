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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

/**
 * Stores Notes, their text, Links and Chunks in the Collection's own schema; vectors live in pgvector columns, full
 * text in {@code tsvector} columns built in each Note's language. A table of its own records how the index was built.
 * Each statement stands above the method that binds it and binds every value once. Table names cannot be bound, so
 * the schema is validated once and is the only name spliced in; the vector dimension is the only other value.
 */
@ApplicationScoped
@SecondaryAdapter
public class PostgresIndex
        implements IndexedNotes, IndexBuild, ChunkVectors, ChunkFullText, Catalog, IndexedLinks, NoteTexts {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]*");

    private final AgroalDataSource dataSource;
    private final String schema;

    public PostgresIndex(AgroalDataSource dataSource, CollectionConfig collection) {
        this.dataSource = dataSource;
        this.schema = validated(collection.schema());
    }

    /** A Note indexed before content hashes were stored has none, so it is left out and indexed again. */
    private static final String CONTENT_HASHES = "SELECT path, content_hash FROM {note} WHERE content_hash IS NOT NULL";

    @Override
    public Map<NotePath, ContentHash> contentHashes() {
        return query(CONTENT_HASHES, select -> {}, PostgresIndex::toContentHash).stream()
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

    private static final String REMOVE_NOTES = "DELETE FROM {note} WHERE path = ANY (?)";

    @Override
    public void remove(Collection<NotePath> notes) {
        Transaction.run(dataSource, connection -> {
            try (PreparedStatement delete = connection.prepareStatement(sql(REMOVE_NOTES))) {
                delete.setArray(1, textArray(connection, notes.stream().map(NotePath::value).toList()));
                delete.executeUpdate();
            }
        });
    }

    private static final String FINGERPRINT = """
            SELECT embedding_model, dimension, heading_level, minimum_characters, maximum_characters, overlap_characters
            FROM {schema}.index_fingerprint""";

    @Override
    public Optional<IndexFingerprint> fingerprint() {
        return query(FINGERPRINT, select -> {}, PostgresIndex::toFingerprint).stream().findFirst();
    }

    private static final String FORGET_FINGERPRINT = "DELETE FROM {schema}.index_fingerprint";

    private static final String REMOVE_CHUNKS = "DELETE FROM {chunk}";

    /**
     * Only possible on an empty table, as a vector cannot change its dimension. A type modifier cannot be bound as a
     * parameter either, but a number cannot smuggle in any SQL.
     */
    private static final String RESIZE_EMBEDDING = "ALTER TABLE {chunk} ALTER COLUMN embedding TYPE public.vector(%d)";

    /** A value no SHA-256 can equal, as in the migrations V4 and V8. */
    private static final String MARK_NOTES_CHANGED = "UPDATE {note} SET content_hash = 're-index'";

    /** One transaction, so a crash leaves either the old index with its fingerprint or an empty one without. */
    @Override
    public void startOver(int dimension) {
        Transaction.run(dataSource, connection -> {
            execute(connection, FORGET_FINGERPRINT);
            execute(connection, REMOVE_CHUNKS);
            execute(connection, RESIZE_EMBEDDING.formatted(dimension));
            execute(connection, MARK_NOTES_CHANGED);
        });
    }

    @Override
    public void complete(IndexFingerprint fingerprint) {
        Transaction.run(dataSource, connection -> {
            execute(connection, FORGET_FINGERPRINT);
            insertFingerprint(connection, fingerprint);
        });
    }

    /**
     * Sorts by the distance to the query vector and cuts at the limit before it applies the Minimum Similarity. The
     * order is ascending, so the threshold only drops the tail: the result is the same as filtering first, and the
     * vector is bound once.
     */
    private static final String NEAREST = """
            SELECT content, note_path, ordinal, heading_path, kind, 1 - distance AS score
            FROM (
                SELECT c.content, c.note_path, c.ordinal, c.heading_path, n.kind, c.embedding <=> ?::vector AS distance
                FROM {chunk} c JOIN {note} n ON n.path = c.note_path
                WHERE n.topic IS NOT DISTINCT FROM coalesce(?, n.topic)
                ORDER BY distance, c.note_path, c.ordinal
                LIMIT ?
            ) nearest
            WHERE 1 - distance >= ?
            ORDER BY distance, note_path, ordinal""";

    @Override
    public List<SearchHit> nearest(float[] query, Optional<Topic> topic, double minimumSimilarity, int limit) {
        return query(NEAREST, select -> {
            select.setString(1, Arrays.toString(query));
            select.setString(2, nameOrNull(topic));
            select.setInt(3, limit);
            select.setDouble(4, minimumSimilarity);
        }, searchHit(SearchHit::bySimilarity));
    }

    /** The terms are parsed once per Note Language in the Collection; each Chunk meets those of its own language. */
    private static final String MATCHING = """
            WITH terms AS (
                SELECT language, plainto_tsquery({schema}.text_search_config(language), ?) AS query
                FROM (SELECT DISTINCT language FROM {note}) languages
            )
            SELECT c.content, c.note_path, c.ordinal, c.heading_path, n.kind, ts_rank_cd(c.full_text, t.query) AS score
            FROM {chunk} c
            JOIN {note} n ON n.path = c.note_path
            JOIN terms t ON t.language = n.language
            WHERE n.topic IS NOT DISTINCT FROM coalesce(?, n.topic)
              AND c.full_text @@ t.query
            ORDER BY score DESC, c.note_path, c.ordinal
            LIMIT ?""";

    @Override
    public List<SearchHit> matching(String terms, Optional<Topic> topic, int limit) {
        return query(MATCHING, select -> {
            select.setString(1, terms);
            select.setString(2, nameOrNull(topic));
            select.setInt(3, limit);
        }, searchHit(SearchHit::byFullTextScore));
    }

    /**
     * Both searches compare {@code n.topic IS NOT DISTINCT FROM coalesce(?, n.topic)}, so a {@code null} name matches
     * every Note, including those in the Vault root.
     */
    private static String nameOrNull(Optional<Topic> topic) {
        return topic.map(Topic::name).orElse(null);
    }

    private static final String TOPICS = """
            SELECT topic, count(*) AS notes FROM {note}
            WHERE topic IS NOT NULL
            GROUP BY topic
            ORDER BY topic""";

    @Override
    public List<TopicCount> topics() {
        return query(TOPICS, select -> {},
                row -> new TopicCount(new Topic(row.getString("topic")), row.getInt("notes")));
    }

    private static final String SOURCE_NOTES = """
            SELECT path, title, published, imported, author FROM {note}
            WHERE kind = 'source' AND topic = ?
            ORDER BY lower(title), path""";

    @Override
    public List<SourceNoteListing> sourceNotes(Topic topic) {
        return query(SOURCE_NOTES, select -> select.setString(1, topic.name()), PostgresIndex::toSourceNoteListing);
    }

    /** Every Note, joined with its Links if it has any. */
    private static final String LINKS_OF_EVERY_NOTE = """
            SELECT n.path, l.target, l.kind, l.anchor
            FROM {note} n LEFT JOIN {link} l ON l.note_path = n.path
            ORDER BY n.path, l.ordinal""";

    @Override
    public Map<NotePath, List<Link>> ofEveryNote() {
        Map<NotePath, List<Link>> linksOfEveryNote = new LinkedHashMap<>();
        List<Map.Entry<NotePath, Optional<Link>>> rows =
                query(LINKS_OF_EVERY_NOTE, select -> {}, PostgresIndex::toNoteLink);
        for (Map.Entry<NotePath, Optional<Link>> row : rows) {
            List<Link> links = linksOfEveryNote.computeIfAbsent(row.getKey(), path -> new ArrayList<>());
            row.getValue().ifPresent(links::add);
        }
        return linksOfEveryNote;
    }

    /** A Note indexed before its text was stored has none, so it is not found until it is indexed again. */
    private static final String NOTE_TEXT = "SELECT text FROM {note} WHERE path = ? AND text IS NOT NULL";

    @Override
    public Optional<NoteText> text(NotePath path) {
        return query(NOTE_TEXT, select -> select.setString(1, path.value()),
                row -> new NoteText(row.getString("text"))).stream().findFirst();
    }

    private void execute(Connection connection, String statement) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(sql(statement))) {
            update.executeUpdate();
        }
    }

    private static final String INSERT_FINGERPRINT = """
            INSERT INTO {schema}.index_fingerprint (embedding_model, dimension, heading_level, minimum_characters,
                maximum_characters, overlap_characters)
            VALUES (?, ?, ?, ?, ?, ?)""";

    private void insertFingerprint(Connection connection, IndexFingerprint fingerprint) throws SQLException {
        ChunkingRules chunking = fingerprint.chunking();
        try (PreparedStatement insert = connection.prepareStatement(sql(INSERT_FINGERPRINT))) {
            insert.setString(1, fingerprint.embeddingModel());
            insert.setInt(2, fingerprint.dimension());
            insert.setInt(3, chunking.headingLevel());
            insert.setInt(4, chunking.minimumCharacters());
            insert.setInt(5, chunking.maximumCharacters());
            insert.setInt(6, chunking.overlapCharacters());
            insert.executeUpdate();
        }
    }

    private static final String DELETE_NOTE = "DELETE FROM {note} WHERE path = ?";

    private void deleteNote(Connection connection, NotePath path) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(sql(DELETE_NOTE))) {
            delete.setString(1, path.value());
            delete.executeUpdate();
        }
    }

    private static final String INSERT_NOTE = """
            INSERT INTO {note} (path, topic, kind, title, imported, published, author, language, content_hash, text)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private void insertNote(Connection connection, Note note, String text, ContentHash hash) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(sql(INSERT_NOTE))) {
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

    private static final String INSERT_LINK = """
            INSERT INTO {link} (note_path, ordinal, target, kind, anchor)
            VALUES (?, ?, ?, ?, ?)""";

    private void insertLinks(Connection connection, NotePath path, List<Link> links) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(sql(INSERT_LINK))) {
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

    /** The values are named once in {@code v}, so the full text is built from the same headings and content. */
    private static final String INSERT_CHUNK = """
            INSERT INTO {chunk} (note_path, ordinal, heading_path, content, embedding, embedding_model, full_text)
            SELECT v.note_path, v.ordinal, v.heading_path, v.content, v.embedding, v.embedding_model,
                {schema}.chunk_full_text(v.language, v.title, v.heading_path, v.content)
            FROM (VALUES (?::text, ?::integer, ?::text[], ?::text, ?::vector, ?::text, ?::text, ?::text))
                AS v (note_path, ordinal, heading_path, content, embedding, embedding_model, language, title)""";

    private void insertChunks(Connection connection, Note note, List<EmbeddedChunk> chunks, String embeddingModel)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(sql(INSERT_CHUNK))) {
            // What all Chunks of the Note share is bound once; a batch keeps it for every Chunk.
            insert.setString(1, note.path().value());
            insert.setString(6, embeddingModel);
            insert.setString(7, note.language().code());
            insert.setString(8, note.title());
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
                PreparedStatement select = connection.prepareStatement(sql(statement))) {
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

    /** The statement in the Collection's schema: {@code {note}}, {@code {chunk}} and {@code {link}} name its tables. */
    private String sql(String statement) {
        return statement
                .replace("{note}", schema + ".note")
                .replace("{chunk}", schema + ".chunk")
                .replace("{link}", schema + ".link")
                .replace("{schema}", schema);
    }

    private static String validated(String schema) {
        if (!IDENTIFIER.matcher(schema).matches()) {
            throw new IllegalArgumentException("Invalid schema name: " + schema);
        }
        return schema;
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
