-- How the index was built: the embedding model, its vector dimension and the chunking rules. It holds one row once a
-- Re-Index completed, and none before, so an index that predates this table is re-indexed on the next start-up.
CREATE TABLE index_fingerprint (
    embedding_model    TEXT NOT NULL,
    dimension          INT  NOT NULL,
    heading_level      INT  NOT NULL,
    minimum_characters INT  NOT NULL,
    maximum_characters INT  NOT NULL,
    overlap_characters INT  NOT NULL
);
