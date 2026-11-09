-- read_note answers from the index, so every Note keeps its full raw text. Notes indexed before this column existed
-- have none, so a value no SHA-256 can equal makes the next Reconciliation index them again (see V4).
ALTER TABLE note ADD COLUMN text TEXT;
UPDATE note SET content_hash = 'note-text-added';
