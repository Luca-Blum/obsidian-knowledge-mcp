-- What OAuth remembers across restarts (ADR 0005). Secrets are stored only as SHA-256 hashes in hex.

-- Clients that registered themselves, such as claude.ai. A Client that asked for a secret has its hash
CREATE TABLE oauth_client (
    client_id     TEXT PRIMARY KEY,
    name          TEXT        NOT NULL,
    redirect_uris TEXT[]      NOT NULL,
    secret_hash   TEXT,
    registered    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Codes from the consent page, waiting to be redeemed once
CREATE TABLE oauth_code (
    code_hash      TEXT PRIMARY KEY,
    client_id      TEXT        NOT NULL REFERENCES oauth_client ON DELETE CASCADE,
    redirect_uri   TEXT        NOT NULL,
    code_challenge TEXT        NOT NULL,
    expires        TIMESTAMPTZ NOT NULL
);

-- One row per Grant: the access token and the refresh token issued together
CREATE TABLE oauth_grant (
    refresh_hash    TEXT PRIMARY KEY,
    client_id       TEXT        NOT NULL REFERENCES oauth_client ON DELETE CASCADE,
    access_hash     TEXT        NOT NULL UNIQUE,
    access_expires  TIMESTAMPTZ NOT NULL,
    refresh_expires TIMESTAMPTZ NOT NULL
);

-- The hash of the passphrase the Grants were given under; a different one at start-up forgets every Grant
CREATE TABLE oauth_passphrase (
    hash TEXT NOT NULL
);
