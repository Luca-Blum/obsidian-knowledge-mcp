-- The Splitter rules changed, so every Note is split and embedded again by the next Reconciliation. A value no SHA-256
-- can equal, rather than NULL, keeps the Notes in the comparison, so one deleted from the Vault meanwhile is removed.
UPDATE note SET content_hash = 'splitter-rules-changed';
