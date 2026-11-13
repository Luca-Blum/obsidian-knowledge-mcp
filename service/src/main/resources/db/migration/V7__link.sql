CREATE TABLE link (
    note_path TEXT NOT NULL REFERENCES note (path) ON DELETE CASCADE,
    ordinal   INT  NOT NULL,
    target    TEXT NOT NULL,
    kind      TEXT NOT NULL CHECK (kind IN ('note', 'embed', 'file')),
    anchor    TEXT,
    PRIMARY KEY (note_path, ordinal)
);

-- Notes indexed before Links were stored have none, so the next Reconciliation indexes every Note again, as V4 does.
UPDATE note SET content_hash = 'links-stored';
