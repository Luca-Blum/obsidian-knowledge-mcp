package io.github.lucablum.knowledge.adapter.postgres;

import java.util.List;

/** The SQL of OAuth's tables in one Collection's schema, which {@link Statements} validates as it does for the index. */
@SuppressWarnings({"SqlNoDataSourceInspection", "SqlResolve"})
record GrantStatements(
        String insertClient,
        String client,
        String insertCode,
        String takeCode,
        String removeExpiredCodes,
        String insertGrant,
        String takeGrant,
        String removeExpiredGrants,
        String admits,
        String passphrase,
        List<String> forgetAll,
        String insertPassphrase) {

    static GrantStatements forSchema(String schema) {
        Statements.requireIdentifier(schema);
        String client = schema + ".oauth_client";
        String code = schema + ".oauth_code";
        String grant = schema + ".oauth_grant";
        String passphrase = schema + ".oauth_passphrase";
        return new GrantStatements(
                "INSERT INTO " + client + " (client_id, name, redirect_uris, secret_hash) VALUES (?, ?, ?, ?)",
                "SELECT client_id, name, redirect_uris, secret_hash FROM " + client + " WHERE client_id = ?",
                "INSERT INTO " + code + " (code_hash, client_id, redirect_uri, code_challenge, expires) "
                        + "VALUES (?, ?, ?, ?, ?)",
                "DELETE FROM " + code + " WHERE code_hash = ? "
                        + "RETURNING code_hash, client_id, redirect_uri, code_challenge, expires",
                "DELETE FROM " + code + " WHERE expires <= now()",
                "INSERT INTO " + grant + " (refresh_hash, client_id, access_hash, access_expires, refresh_expires) "
                        + "VALUES (?, ?, ?, ?, ?)",
                "DELETE FROM " + grant + " WHERE refresh_hash = ? "
                        + "RETURNING refresh_hash, client_id, access_hash, access_expires, refresh_expires",
                "DELETE FROM " + grant + " WHERE refresh_expires <= now()",
                "SELECT 1 FROM " + grant + " WHERE access_hash = ? AND access_expires > ?",
                "SELECT hash FROM " + passphrase,
                List.of("DELETE FROM " + code, "DELETE FROM " + grant, "DELETE FROM " + passphrase),
                "INSERT INTO " + passphrase + " (hash) VALUES (?)");
    }
}
