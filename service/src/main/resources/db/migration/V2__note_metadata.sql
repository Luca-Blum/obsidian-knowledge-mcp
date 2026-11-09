-- The index is rebuilt from the Vault on start-up, so existing Notes are dropped instead of given made-up values.
DELETE FROM note;

ALTER TABLE note
    ADD COLUMN kind      TEXT NOT NULL CHECK (kind IN ('source', 'own')),
    ADD COLUMN title     TEXT NOT NULL,
    ADD COLUMN imported  DATE,
    ADD COLUMN published TEXT,
    ADD COLUMN author    TEXT,
    ADD COLUMN language  TEXT NOT NULL;
