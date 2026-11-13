-- The text search configuration of a Note Language: its stemming and stop words. A new Note Language needs a line here.
CREATE FUNCTION text_search_config(language TEXT) RETURNS regconfig
    LANGUAGE sql IMMUTABLE STRICT
    RETURN CASE language WHEN 'de' THEN 'german'::regconfig WHEN 'en' THEN 'english'::regconfig END;

-- A Chunk's full text in its Note's language. The Title weighs most (A), then the heading path (B), then the body (D).
CREATE FUNCTION chunk_full_text(language TEXT, title TEXT, heading_path TEXT[], content TEXT) RETURNS tsvector
    LANGUAGE sql IMMUTABLE
    RETURN setweight(to_tsvector(text_search_config(language), title), 'A')
        || setweight(to_tsvector(text_search_config(language), array_to_string(heading_path, ' ')), 'B')
        || to_tsvector(text_search_config(language), content);

-- Built from what is already stored, so the existing Chunks need neither splitting nor embedding again.
ALTER TABLE chunk ADD COLUMN full_text tsvector;
UPDATE chunk c
SET full_text = chunk_full_text(n.language, n.title, c.heading_path, c.content)
FROM note n
WHERE n.path = c.note_path;
ALTER TABLE chunk ALTER COLUMN full_text SET NOT NULL;

CREATE INDEX chunk_full_text_index ON chunk USING gin (full_text);
