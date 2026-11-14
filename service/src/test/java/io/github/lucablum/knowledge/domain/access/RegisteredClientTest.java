package io.github.lucablum.knowledge.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegisteredClientTest {

    private static final URI CALLBACK = URI.create("https://claude.ai/api/mcp/auth_callback");

    @ParameterizedTest
    @ValueSource(strings = {
        "https://claude.ai/api/mcp/auth_callback",
        "http://localhost:33418/callback",
        "http://127.0.0.1:8080/callback",
        "http://[::1]/callback"})
    void whenRegister_givenHttpsOrLoopbackRedirect_thenAccept(String redirect) {
        assertThat(client(URI.create(redirect)).allowsRedirectTo(URI.create(redirect))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://claude.ai/callback",
        "https://claude.ai/callback#fragment",
        "javascript:alert(1)",
        "/relative/callback"})
    void whenRegister_givenAnyOtherRedirect_thenReject(String redirect) {
        assertThatThrownBy(() -> client(URI.create(redirect))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whenRegister_givenNoRedirect_thenReject() {
        assertThatThrownBy(() -> new RegisteredClient("id", "claude.ai", List.of(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whenCheckRedirect_givenOneThatWasNotRegistered_thenRefuse() {
        assertThat(client(CALLBACK).allowsRedirectTo(URI.create("https://claude.ai/other"))).isFalse();
    }

    @Test
    void whenAuthenticate_givenClientWithoutSecret_thenAcceptWithoutOne() {
        assertThat(client(CALLBACK).authenticates(Optional.empty())).isTrue();
    }

    @Test
    void whenAuthenticate_givenClientWithSecret_thenRequireThatSecret() {
        RegisteredClient client =
                new RegisteredClient("id", "claude.ai", List.of(CALLBACK), Optional.of(SecretHash.of("secret")));

        assertThat(client.authenticates(Optional.of("secret"))).isTrue();
        assertThat(client.authenticates(Optional.of("other"))).isFalse();
        assertThat(client.authenticates(Optional.empty())).isFalse();
    }

    private static RegisteredClient client(URI redirect) {
        return new RegisteredClient("id", "claude.ai", List.of(redirect), Optional.empty());
    }
}
