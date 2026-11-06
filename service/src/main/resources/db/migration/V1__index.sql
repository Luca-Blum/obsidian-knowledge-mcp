CREATE EXTENSION IF NOT EXISTS vector SCHEMA public;

CREATE TABLE note (
    path  TEXT PRIMARY KEY,
    topic TEXT
);

CREATE TABLE chunk (
    note_path       TEXT NOT NULL REFERENCES note (path) ON DELETE CASCADE,
    ordinal         INT  NOT NULL,
    heading_path    TEXT[] NOT NULL,
    content         TEXT NOT NULL,
    embedding       public.vector(${dimension}) NOT NULL,
    embedding_model TEXT NOT NULL,
    PRIMARY KEY (note_path, ordinal)
);
