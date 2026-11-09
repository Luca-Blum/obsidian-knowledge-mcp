package io.github.lucablum.knowledge.adapter.postgres;

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
        String topics,
        String sourceNotes,
        String linksOfEveryNote) {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]*");

    private static final String DELETE_NOTE_TEMPLATE = "DELETE FROM {note} WHERE path = ?";

    private static final String INSERT_NOTE_TEMPLATE = """
            INSERT INTO {note} (path, topic, kind, title, imported, published, author, language, content_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private static final String INSERT_CHUNK_TEMPLATE = """
            INSERT INTO {chunk} (note_path, ordinal, heading_path, content, embedding, embedding_model)
            VALUES (?, ?, ?, ?, ?::vector, ?)""";

    private static final String INSERT_LINK_TEMPLATE = """
            INSERT INTO {link} (note_path, ordinal, target, kind, anchor)
            VALUES (?, ?, ?, ?, ?)""";

    /** A Note indexed before content hashes were stored has none, so it is left out and indexed again. */
    private static final String CONTENT_HASHES_TEMPLATE =
            "SELECT path, content_hash FROM {note} WHERE content_hash IS NOT NULL";

    private static final String REMOVE_NOTES_TEMPLATE = "DELETE FROM {note} WHERE path = ANY (?)";

    private static final String NEAREST_TEMPLATE = """
            SELECT c.content, c.note_path, c.heading_path, n.kind, 1 - (c.embedding <=> ?::vector) AS score
            FROM {chunk} c JOIN {note} n ON n.path = c.note_path
            WHERE (?::text IS NULL OR n.topic = ?)
              AND 1 - (c.embedding <=> ?::vector) >= ?
            ORDER BY c.embedding <=> ?::vector, c.note_path, c.ordinal
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
                inSchema(TOPICS_TEMPLATE, schema),
                inSchema(SOURCE_NOTES_TEMPLATE, schema),
                inSchema(LINKS_OF_EVERY_NOTE_TEMPLATE, schema));
    }

    private static String inSchema(String template, String schema) {
        return template
                .replace("{note}", schema + ".note")
                .replace("{chunk}", schema + ".chunk")
                .replace("{link}", schema + ".link");
    }
}
