-- Notes indexed before this column existed have no hash, so the next Reconciliation indexes them again.
ALTER TABLE note ADD COLUMN content_hash TEXT;
