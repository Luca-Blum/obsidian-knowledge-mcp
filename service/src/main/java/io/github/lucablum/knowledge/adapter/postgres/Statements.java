package io.github.lucablum.knowledge.adapter.postgres;

import java.util.function.IntFunction;
import java.util.regex.Pattern;

/**
 * The SQL of one Collection. Table names cannot be bound as parameters, so the schema is validated
 * once here and is the only text spliced into the statements.
 */
@SuppressWarnings({"SqlNoDataSourceInspection", "SqlResolve"})
record Statements(
        String deleteNote,
        String insertNote,
        String insertChunk,
        String insertLink,
        String contentHashes,
        String removeNotes,
        String nearest,
        String matching,
        String topics,
        String sourceNotes,
        String linksOfEveryNote,
        String noteText,
        String fingerprint,
        String forgetFingerprint,
        String insertFingerprint,
        String removeChunks,
        IntFunction<String> resizeEmbedding,
        String markNotesChanged) {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]*");

    private static final String DELETE_NOTE_TEMPLATE = "DELETE FROM {note} WHERE path = ?";

    private static final String INSERT_NOTE_TEMPLATE = """
            INSERT INTO {note} (path, topic, kind, title, imported, published, author, language, content_hash, text)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private static final String INSERT_CHUNK_TEMPLATE = """
            INSERT INTO {chunk} (note_path, ordinal, heading_path, content, embedding, embedding_model, full_text)
            VALUES (?, ?, ?, ?, ?::vector, ?, {schema}.chunk_full_text(?, ?, ?, ?))""";

    private static final String INSERT_LINK_TEMPLATE = """
            INSERT INTO {link} (note_path, ordinal, target, kind, anchor)
            VALUES (?, ?, ?, ?, ?)""";

    /** A Note indexed before content hashes were stored has none, so it is left out and indexed again. */
    private static final String CONTENT_HASHES_TEMPLATE =
            "SELECT path, content_hash FROM {note} WHERE content_hash IS NOT NULL";

    private static final String REMOVE_NOTES_TEMPLATE = "DELETE FROM {note} WHERE path = ANY (?)";

    private static final String NEAREST_TEMPLATE = """
            SELECT c.content, c.note_path, c.ordinal, c.heading_path, n.kind, 1 - (c.embedding <=> ?::vector) AS score
            FROM {chunk} c JOIN {note} n ON n.path = c.note_path
            WHERE (?::text IS NULL OR n.topic = ?)
              AND 1 - (c.embedding <=> ?::vector) >= ?
            ORDER BY c.embedding <=> ?::vector, c.note_path, c.ordinal
            LIMIT ?""";

    /** The terms are parsed once per Note Language in the Collection; each Chunk meets those of its own language. */
    private static final String MATCHING_TEMPLATE = """
            WITH terms AS (
                SELECT language, plainto_tsquery({schema}.text_search_config(language), ?) AS query
                FROM (SELECT DISTINCT language FROM {note}) languages
            )
            SELECT c.content, c.note_path, c.ordinal, c.heading_path, n.kind, ts_rank_cd(c.full_text, t.query) AS score
            FROM {chunk} c
            JOIN {note} n ON n.path = c.note_path
            JOIN terms t ON t.language = n.language
            WHERE (?::text IS NULL OR n.topic = ?)
              AND c.full_text @@ t.query
            ORDER BY score DESC, c.note_path, c.ordinal
            LIMIT ?""";

    private static final String TOPICS_TEMPLATE = """
            SELECT topic, count(*) AS notes FROM {note}
            WHERE topic IS NOT NULL
            GROUP BY topic
            ORDER BY topic""";

    private static final String SOURCE_NOTES_TEMPLATE = """
            SELECT path, title, published, imported, author FROM {note}
            WHERE kind = 'source' AND topic = ?
            ORDER BY lower(title), path""";

    /** Every Note, joined with its Links if it has any. */
    private static final String LINKS_OF_EVERY_NOTE_TEMPLATE = """
            SELECT n.path, l.target, l.kind, l.anchor
            FROM {note} n LEFT JOIN {link} l ON l.note_path = n.path
            ORDER BY n.path, l.ordinal""";

    /** A Note indexed before its text was stored has none, so it is not found until it is indexed again. */
    private static final String NOTE_TEXT_TEMPLATE = "SELECT text FROM {note} WHERE path = ? AND text IS NOT NULL";

    private static final String FINGERPRINT_TEMPLATE = """
            SELECT embedding_model, dimension, heading_level, minimum_characters, maximum_characters, overlap_characters
            FROM {schema}.index_fingerprint""";

    private static final String FORGET_FINGERPRINT_TEMPLATE = "DELETE FROM {schema}.index_fingerprint";

    private static final String INSERT_FINGERPRINT_TEMPLATE = """
            INSERT INTO {schema}.index_fingerprint (embedding_model, dimension, heading_level, minimum_characters,
                maximum_characters, overlap_characters)
            VALUES (?, ?, ?, ?, ?, ?)""";

    private static final String REMOVE_CHUNKS_TEMPLATE = "DELETE FROM {chunk}";

    /**
     * Only possible on an empty table, as a vector cannot change its dimension. A type modifier cannot be bound as a
     * parameter either, but a number cannot smuggle in any SQL.
     */
    private static final String RESIZE_EMBEDDING_TEMPLATE =
            "ALTER TABLE {chunk} ALTER COLUMN embedding TYPE public.vector({dimension})";

    /** A value no SHA-256 can equal, as in the migrations V4 and V8. */
    private static final String MARK_NOTES_CHANGED_TEMPLATE = "UPDATE {note} SET content_hash = 're-index'";

    static Statements forSchema(String schema) {
        if (!IDENTIFIER.matcher(schema).matches()) {
            throw new IllegalArgumentException("Invalid schema name: " + schema);
        }
        return new Statements(
                inSchema(DELETE_NOTE_TEMPLATE, schema),
                inSchema(INSERT_NOTE_TEMPLATE, schema),
                inSchema(INSERT_CHUNK_TEMPLATE, schema),
                inSchema(INSERT_LINK_TEMPLATE, schema),
                inSchema(CONTENT_HASHES_TEMPLATE, schema),
                inSchema(REMOVE_NOTES_TEMPLATE, schema),
                inSchema(NEAREST_TEMPLATE, schema),
                inSchema(MATCHING_TEMPLATE, schema),
                inSchema(TOPICS_TEMPLATE, schema),
                inSchema(SOURCE_NOTES_TEMPLATE, schema),
                inSchema(LINKS_OF_EVERY_NOTE_TEMPLATE, schema),
                inSchema(NOTE_TEXT_TEMPLATE, schema),
                inSchema(FINGERPRINT_TEMPLATE, schema),
                inSchema(FORGET_FINGERPRINT_TEMPLATE, schema),
                inSchema(INSERT_FINGERPRINT_TEMPLATE, schema),
                inSchema(REMOVE_CHUNKS_TEMPLATE, schema),
                resizeEmbedding(schema),
                inSchema(MARK_NOTES_CHANGED_TEMPLATE, schema));
    }

    private static IntFunction<String> resizeEmbedding(String schema) {
        String statement = inSchema(RESIZE_EMBEDDING_TEMPLATE, schema);
        return dimension -> statement.replace("{dimension}", Integer.toString(dimension));
    }

    private static String inSchema(String template, String schema) {
        return template
                .replace("{note}", schema + ".note")
                .replace("{chunk}", schema + ".chunk")
                .replace("{link}", schema + ".link")
                .replace("{schema}", schema);
    }
}
