package io.github.lucablum.knowledge.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import io.agroal.api.AgroalDataSource;
import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.domain.access.AuthorizationCode;
import io.github.lucablum.knowledge.domain.access.CodeChallenge;
import io.github.lucablum.knowledge.domain.access.Grant;
import io.github.lucablum.knowledge.domain.access.IssuedTokens;
import io.github.lucablum.knowledge.domain.access.RegisteredClient;
import io.github.lucablum.knowledge.domain.access.SecretHash;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** OAuth's records live in the Collection's schema, so a new instance on the same database finds them again. */
@QuarkusTest
class PostgresGrantsTest {

    private static final URI CALLBACK = URI.create("https://claude.ai/api/mcp/auth_callback");

    /** Postgres keeps microseconds and clears what expired by its own clock, so the test starts now at that precision. */
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Inject
    AgroalDataSource dataSource;

    @Inject
    CollectionConfig collection;

    private PostgresGrants grants;

    private RegisteredClient client;

    @BeforeEach
    void startWithoutRecords() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute(
                    "TRUNCATE %1$s.oauth_client, %1$s.oauth_code, %1$s.oauth_grant, %1$s.oauth_passphrase"
                            .formatted(collection.schema()));
        }
        grants = new PostgresGrants(dataSource, collection);
        client = new RegisteredClient("client-1", "claude.ai", List.of(CALLBACK), Optional.of(SecretHash.of("s")));
        grants.register(client);
    }

    @Test
    void whenReadClient_givenNewInstanceOnTheSameDatabase_thenFindItAsRegistered() {
        assertThat(restarted().client("client-1")).contains(client);
        assertThat(restarted().client("unknown")).isEmpty();
    }

    @Test
    void whenTakeCode_givenCodeRemembered_thenReturnItOnceOnly() {
        AuthorizationCode code = new AuthorizationCode(SecretHash.of("code"), "client-1", CALLBACK,
                new CodeChallenge("challenge"), NOW.plus(AuthorizationCode.LIFETIME));
        grants.remember(code);

        assertThat(restarted().takeCode(SecretHash.of("code"))).contains(code);
        assertThat(grants.takeCode(SecretHash.of("code"))).isEmpty();
    }

    @Test
    void whenTakeGrant_givenGrantSaved_thenReturnItOnceOnly() {
        Grant grant = IssuedTokens.issueTo("client-1", NOW).grant();
        grants.save(grant);

        assertThat(restarted().takeGrant(grant.refreshToken())).contains(grant);
        assertThat(grants.takeGrant(grant.refreshToken())).isEmpty();
    }

    @Test
    void whenAdmit_givenAccessTokenOfASavedGrant_thenAdmitUntilItExpires() {
        IssuedTokens tokens = IssuedTokens.issueTo("client-1", NOW);
        grants.save(tokens.grant());
        SecretHash accessToken = SecretHash.of(tokens.accessToken());

        assertThat(restarted().admits(accessToken, NOW)).isTrue();
        assertThat(grants.admits(accessToken, tokens.grant().accessExpires())).isFalse();
        assertThat(grants.admits(SecretHash.of("unknown"), NOW)).isFalse();
    }

    @Test
    void whenSave_givenIssuedTokens_thenStoreNeitherInPlainText() throws SQLException {
        IssuedTokens tokens = IssuedTokens.issueTo("client-1", NOW);
        grants.save(tokens.grant());

        assertThat(storedGrantValues()).isNotEmpty().doesNotContain(tokens.accessToken(), tokens.refreshToken());
    }

    @Test
    void whenForgetAll_givenCodesAndGrants_thenKeepOnlyClientsAndTheNewPassphrase() {
        grants.remember(new AuthorizationCode(SecretHash.of("code"), "client-1", CALLBACK,
                new CodeChallenge("challenge"), NOW.plus(AuthorizationCode.LIFETIME)));
        IssuedTokens tokens = IssuedTokens.issueTo("client-1", NOW);
        grants.save(tokens.grant());

        grants.forgetAll(SecretHash.of("new passphrase"));

        PostgresGrants restarted = restarted();
        assertThat(restarted.takeCode(SecretHash.of("code"))).isEmpty();
        assertThat(restarted.admits(SecretHash.of(tokens.accessToken()), NOW)).isFalse();
        assertThat(restarted.client("client-1")).isPresent();
        assertThat(restarted.passphrase()).contains(SecretHash.of("new passphrase"));
    }

    @Test
    void whenReadPassphrase_givenNoneRecordedYet_thenReturnEmpty() {
        assertThat(grants.passphrase()).isEmpty();
    }

    /** What an instance started after this one would see. */
    private PostgresGrants restarted() {
        return new PostgresGrants(dataSource, collection);
    }

    private List<String> storedGrantValues() throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                ResultSet rows = connection.createStatement().executeQuery(
                        "SELECT * FROM " + collection.schema() + ".oauth_grant")) {
            while (rows.next()) {
                for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                    values.add(rows.getString(column));
                }
            }
        }
        return values;
    }
}
