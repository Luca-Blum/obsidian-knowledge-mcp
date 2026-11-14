package io.github.lucablum.knowledge.application.access;

import io.github.lucablum.knowledge.domain.access.AuthorizationCode;
import io.github.lucablum.knowledge.domain.access.Grant;
import io.github.lucablum.knowledge.domain.access.IssuedTokens;
import io.github.lucablum.knowledge.domain.access.Passphrase;
import io.github.lucablum.knowledge.domain.access.RegisteredClient;
import io.github.lucablum.knowledge.domain.access.SecretHash;
import io.github.lucablum.knowledge.domain.access.Secrets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jmolecules.architecture.hexagonal.Application;

/**
 * Anyone may register as a Client, but only the User, who knows the Collection's passphrase, can let one in. Codes and
 * refresh tokens are taken out of the store when used, so each works once.
 */
@Application
public class PassphraseConsent implements AuthorizeClients {

    private final Grants grants;
    private final Passphrase passphrase;
    private final Clock clock;

    public PassphraseConsent(Grants grants, Passphrase passphrase, Clock clock) {
        this.grants = grants;
        this.passphrase = passphrase;
        this.clock = clock;
    }

    @Override
    public Registration register(ClientRegistration registration) {
        Optional<String> secret = registration.wantsSecret() ? Optional.of(Secrets.generate()) : Optional.empty();
        RegisteredClient client = new RegisteredClient(UUID.randomUUID().toString(), registration.name(),
                registration.redirectUris(), secret.map(SecretHash::of));
        grants.register(client);
        return new Registration(client, secret);
    }

    @Override
    public Optional<RegisteredClient> client(String clientId) {
        return grants.client(clientId);
    }

    @Override
    public Optional<String> consent(AuthorizationRequest request, String attempt) {
        if (!passphrase.admits(attempt) || !redirectRegistered(request)) {
            return Optional.empty();
        }
        String code = Secrets.generate();
        grants.remember(new AuthorizationCode(SecretHash.of(code), request.clientId(), request.redirectUri(),
                request.challenge(), now().plus(AuthorizationCode.LIFETIME)));
        return Optional.of(code);
    }

    @Override
    public Optional<IssuedTokens> redeem(CodeRedemption redemption) {
        ClientCredentials credentials = redemption.client();
        return grants.takeCode(SecretHash.of(redemption.code()))
                .filter(code -> code.redeemableBy(
                        credentials.clientId(), redemption.redirectUri(), redemption.verifier(), now()))
                .filter(code -> authenticated(credentials))
                .map(code -> issueTo(code.clientId()));
    }

    @Override
    public Optional<IssuedTokens> refresh(String refreshToken, ClientCredentials credentials) {
        return grants.takeGrant(SecretHash.of(refreshToken))
                .filter(grant -> grant.clientId().equals(credentials.clientId()))
                .filter(grant -> grant.refreshableAt(now()))
                .filter(grant -> authenticated(credentials))
                .map(grant -> issueTo(grant.clientId()));
    }

    @Override
    public boolean admits(String accessToken) {
        return grants.admits(SecretHash.of(accessToken), now());
    }

    @Override
    public void forgetGrantsIfPassphraseChanged() {
        SecretHash current = passphrase.hash();
        if (!grants.passphrase().equals(Optional.of(current))) {
            grants.forgetAll(current);
        }
    }

    private boolean redirectRegistered(AuthorizationRequest request) {
        return grants.client(request.clientId())
                .filter(client -> client.allowsRedirectTo(request.redirectUri()))
                .isPresent();
    }

    private boolean authenticated(ClientCredentials credentials) {
        return grants.client(credentials.clientId())
                .filter(client -> client.authenticates(credentials.secret()))
                .isPresent();
    }

    private IssuedTokens issueTo(String clientId) {
        IssuedTokens tokens = IssuedTokens.issueTo(clientId, now());
        grants.save(tokens.grant());
        return tokens;
    }

    private Instant now() {
        return clock.instant();
    }
}
