package io.github.lucablum.knowledge.application.access;

import io.github.lucablum.knowledge.domain.access.IssuedTokens;
import io.github.lucablum.knowledge.domain.access.RegisteredClient;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/**
 * Lets a Client such as claude.ai into this Collection through OAuth: it registers itself, the User consents on a page,
 * and the Client trades the code for tokens. Every refusal is an empty result, so a caller cannot tell a wrong guess
 * from a missing record.
 */
@PrimaryPort
public interface AuthorizeClients {

    /** Registers a Client that asked for it, with a secret if it wants to authenticate with one. */
    Registration register(ClientRegistration registration);

    Optional<RegisteredClient> client(String clientId);

    /** The authorization code for this request, if the passphrase is right and the redirect was registered. */
    Optional<String> consent(AuthorizationRequest request, String passphrase);

    /** Tokens for a code, which works only once, only for its Client and only with the PKCE verifier. */
    Optional<IssuedTokens> redeem(CodeRedemption redemption);

    /** New tokens for a refresh token, which retires it together with the access token issued with it. */
    Optional<IssuedTokens> refresh(String refreshToken, ClientCredentials client);

    /** Whether a request carrying this access token may reach the Collection. */
    boolean admits(String accessToken);

    /** At start-up: a new passphrase locks out every Client that consented under the old one. */
    void forgetGrantsIfPassphraseChanged();
}
