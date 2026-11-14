package io.github.lucablum.knowledge.adapter.postgres;

import io.agroal.api.AgroalDataSource;
import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.access.Grants;
import io.github.lucablum.knowledge.domain.access.AuthorizationCode;
import io.github.lucablum.knowledge.domain.access.CodeChallenge;
import io.github.lucablum.knowledge.domain.access.Grant;
import io.github.lucablum.knowledge.domain.access.RegisteredClient;
import io.github.lucablum.knowledge.domain.access.SecretHash;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

/**
 * Keeps OAuth's Clients, codes and Grants in the Collection's own schema, so they survive restarts and no other
 * Collection can see them. Whenever a code or Grant is stored, those that expired by the database's clock are cleared.
 */
@ApplicationScoped
@SecondaryAdapter
public class PostgresGrants implements Grants {

    private final AgroalDataSource dataSource;
    private final GrantStatements sql;

    public PostgresGrants(AgroalDataSource dataSource, CollectionConfig collection) {
        this.dataSource = dataSource;
        this.sql = GrantStatements.forSchema(collection.schema());
    }

    @Override
    public void register(RegisteredClient client) {
        Transaction.run(dataSource, connection -> {
            try (PreparedStatement insert = connection.prepareStatement(sql.insertClient())) {
                insert.setString(1, client.clientId());
                insert.setString(2, client.name());
                insert.setArray(3, connection.createArrayOf("text",
                        client.redirectUris().stream().map(URI::toString).toArray()));
                insert.setString(4, client.secret().map(SecretHash::value).orElse(null));
                insert.executeUpdate();
            }
        });
    }

    @Override
    public Optional<RegisteredClient> client(String clientId) {
        return queryOne(sql.client(), clientId, PostgresGrants::toClient);
    }

    @Override
    public void remember(AuthorizationCode code) {
        Transaction.run(dataSource, connection -> {
            execute(connection, sql.removeExpiredCodes());
            try (PreparedStatement insert = connection.prepareStatement(sql.insertCode())) {
                insert.setString(1, code.code().value());
                insert.setString(2, code.clientId());
                insert.setString(3, code.redirectUri().toString());
                insert.setString(4, code.challenge().value());
                insert.setTimestamp(5, Timestamp.from(code.expires()));
                insert.executeUpdate();
            }
        });
    }

    @Override
    public Optional<AuthorizationCode> takeCode(SecretHash code) {
        return queryOne(sql.takeCode(), code.value(), PostgresGrants::toCode);
    }

    @Override
    public void save(Grant grant) {
        Transaction.run(dataSource, connection -> {
            execute(connection, sql.removeExpiredGrants());
            try (PreparedStatement insert = connection.prepareStatement(sql.insertGrant())) {
                insert.setString(1, grant.refreshToken().value());
                insert.setString(2, grant.clientId());
                insert.setString(3, grant.accessToken().value());
                insert.setTimestamp(4, Timestamp.from(grant.accessExpires()));
                insert.setTimestamp(5, Timestamp.from(grant.refreshExpires()));
                insert.executeUpdate();
            }
        });
    }

    @Override
    public Optional<Grant> takeGrant(SecretHash refreshToken) {
        return queryOne(sql.takeGrant(), refreshToken.value(), PostgresGrants::toGrant);
    }

    @Override
    public boolean admits(SecretHash accessToken, Instant now) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement select = connection.prepareStatement(sql.admits())) {
            select.setString(1, accessToken.value());
            select.setTimestamp(2, Timestamp.from(now));
            try (ResultSet rows = select.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Checking an access token failed", e);
        }
    }

    @Override
    public Optional<SecretHash> passphrase() {
        return queryOne(sql.passphrase(), null, row -> new SecretHash(row.getString("hash")));
    }

    @Override
    public void forgetAll(SecretHash newPassphrase) {
        Transaction.run(dataSource, connection -> {
            for (String statement : sql.forgetAll()) {
                execute(connection, statement);
            }
            try (PreparedStatement insert = connection.prepareStatement(sql.insertPassphrase())) {
                insert.setString(1, newPassphrase.value());
                insert.executeUpdate();
            }
        });
    }

    private static void execute(Connection connection, String statement) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(statement)) {
            update.executeUpdate();
        }
    }

    /** Runs a statement with at most one parameter that returns at most one row. */
    private <T> Optional<T> queryOne(String statement, String parameter, Row<T> row) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement select = connection.prepareStatement(statement)) {
            if (parameter != null) {
                select.setString(1, parameter);
            }
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? Optional.of(row.read(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed", e);
        }
    }

    private static RegisteredClient toClient(ResultSet row) throws SQLException {
        List<URI> redirectUris = Arrays.stream((Object[]) row.getArray("redirect_uris").getArray())
                .map(uri -> URI.create((String) uri))
                .toList();
        return new RegisteredClient(row.getString("client_id"), row.getString("name"), redirectUris,
                Optional.ofNullable(row.getString("secret_hash")).map(SecretHash::new));
    }

    private static AuthorizationCode toCode(ResultSet row) throws SQLException {
        return new AuthorizationCode(new SecretHash(row.getString("code_hash")), row.getString("client_id"),
                URI.create(row.getString("redirect_uri")), new CodeChallenge(row.getString("code_challenge")),
                row.getTimestamp("expires").toInstant());
    }

    private static Grant toGrant(ResultSet row) throws SQLException {
        return new Grant(row.getString("client_id"),
                new SecretHash(row.getString("access_hash")), row.getTimestamp("access_expires").toInstant(),
                new SecretHash(row.getString("refresh_hash")), row.getTimestamp("refresh_expires").toInstant());
    }

    private interface Row<T> {
        T read(ResultSet row) throws SQLException;
    }
}
