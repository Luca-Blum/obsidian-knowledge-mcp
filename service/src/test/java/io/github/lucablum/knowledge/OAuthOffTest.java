package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.agroal.api.AgroalDataSource;
import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.adapter.postgres.PostgresGrants;
import io.github.lucablum.knowledge.domain.access.IssuedTokens;
import io.github.lucablum.knowledge.domain.access.RegisteredClient;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Without a passphrase OAuth is off, as on a deployment that has not set one: only the static tokens work. */
@QuarkusTest
@TestProfile(OAuthOffTest.WithoutPassphrase.class)
class OAuthOffTest {

    @TestHTTPResource("/.well-known/oauth-authorization-server")
    URI authorizationServer;

    @Inject
    AgroalDataSource dataSource;

    @Inject
    CollectionConfig collection;

    @Test
    void whenDiscover_givenNoPassphrase_thenOfferNoOAuth() throws Exception {
        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(authorizationServer).build(), HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
    }

    @Test
    void whenConnect_givenAccessTokenIssuedWhileOAuthWasOn_thenRejectAsUnauthorized() {
        PostgresGrants grants = new PostgresGrants(dataSource, collection);
        String clientId = UUID.randomUUID().toString();
        grants.register(new RegisteredClient(clientId, "claude.ai",
                List.of(URI.create("https://claude.ai/api/mcp/auth_callback")), Optional.empty()));
        IssuedTokens tokens = IssuedTokens.issueTo(clientId, Instant.now());
        grants.save(tokens.grant());

        TestClients.expectRejected(tokens.accessToken());
    }

    @Test
    void whenListTools_givenStaticClientToken_thenAccept() {
        TestClients.connect()
                .when()
                .toolsList(page -> assertThat(page.findByName("search")).isNotNull())
                .thenAssertResults();
    }

    /** The test profile with the passphrase emptied, as leaving it out of {@code .env} does. */
    public static class WithoutPassphrase implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("collection.oauth.passphrase", "");
        }
    }
}
