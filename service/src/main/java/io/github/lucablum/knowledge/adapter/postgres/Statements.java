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
        String retainOnly,
        String nearest) {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]*");

    private static final String DELETE_NOTE_TEMPLATE = "DELETE FROM {note} WHERE path = ?";

    private static final String INSERT_NOTE_TEMPLATE = "INSERT INTO {note} (path, topic) VALUES (?, ?)";

    private static final String INSERT_CHUNK_TEMPLATE = """
            INSERT INTO {chunk} (note_path, ordinal, heading_path, content, embedding, embedding_model)
            VALUES (?, ?, ?, ?, ?::vector, ?)""";

    private static final String RETAIN_ONLY_TEMPLATE = "DELETE FROM {note} WHERE NOT (path = ANY (?))";

    private static final String NEAREST_TEMPLATE = """
            SELECT c.content, c.note_path, c.heading_path, 1 - (c.embedding <=> ?::vector) AS score
            FROM {chunk} c JOIN {note} n ON n.path = c.note_path
            WHERE (?::text IS NULL OR n.topic = ?)
              AND 1 - (c.embedding <=> ?::vector) >= ?
            ORDER BY c.embedding <=> ?::vector, c.note_path, c.ordinal
            LIMIT ?""";

    static Statements forSchema(String schema) {
        if (!IDENTIFIER.matcher(schema).matches()) {
            throw new IllegalArgumentException("Invalid schema name: " + schema);
        }
        return new Statements(
                inSchema(DELETE_NOTE_TEMPLATE, schema),
                inSchema(INSERT_NOTE_TEMPLATE, schema),
                inSchema(INSERT_CHUNK_TEMPLATE, schema),
                inSchema(RETAIN_ONLY_TEMPLATE, schema),
                inSchema(NEAREST_TEMPLATE, schema));
    }

    private static String inSchema(String template, String schema) {
        return template
                .replace("{note}", schema + ".note")
                .replace("{chunk}", schema + ".chunk");
    }
}
