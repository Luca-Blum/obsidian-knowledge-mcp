package io.github.lucablum.knowledge.domain.access;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A Client that registered itself to ask for access, such as claude.ai. Codes are only ever sent back to one of its
 * redirect URIs, which must be HTTPS or point at the Client's own machine. A Client that asked for a secret must
 * present it when it redeems a code or a refresh token.
 */
@ValueObject
public record RegisteredClient(String clientId, String name, List<URI> redirectUris, Optional<SecretHash> secret) {

    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "[::1]");

    public RegisteredClient {
        if (redirectUris.isEmpty()) {
            throw new IllegalArgumentException("A Client needs at least one redirect URI");
        }
        redirectUris.forEach(RegisteredClient::requireSafeRedirect);
        redirectUris = List.copyOf(redirectUris);
    }

    public boolean allowsRedirectTo(URI redirectUri) {
        return redirectUris.contains(redirectUri);
    }

    public boolean authenticates(Optional<String> presentedSecret) {
        return secret.map(expected -> presentedSecret.filter(expected::matches).isPresent()).orElse(true);
    }

    private static void requireSafeRedirect(URI redirectUri) {
        boolean secure = "https".equals(redirectUri.getScheme()) && redirectUri.getHost() != null;
        boolean loopback = "http".equals(redirectUri.getScheme()) && LOOPBACK.contains(redirectUri.getHost());
        if (!(secure || loopback) || redirectUri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "A redirect URI must be HTTPS, or HTTP to this machine, without a fragment: " + redirectUri);
        }
    }
}
