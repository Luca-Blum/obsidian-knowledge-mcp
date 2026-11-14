package io.github.lucablum.knowledge.application.access;

import io.github.lucablum.knowledge.domain.access.AuthorizationCode;
import io.github.lucablum.knowledge.domain.access.Grant;
import io.github.lucablum.knowledge.domain.access.RegisteredClient;
import io.github.lucablum.knowledge.domain.access.SecretHash;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Keeps Clients, codes and Grants in maps, as Postgres keeps them in tables. */
class InMemoryGrants implements Grants {

    private final Map<String, RegisteredClient> clients = new HashMap<>();
    private final Map<SecretHash, AuthorizationCode> codes = new HashMap<>();
    private final Map<SecretHash, Grant> grantsByRefreshToken = new HashMap<>();
    private SecretHash passphrase;

    @Override
    public void register(RegisteredClient client) {
        clients.put(client.clientId(), client);
    }

    @Override
    public Optional<RegisteredClient> client(String clientId) {
        return Optional.ofNullable(clients.get(clientId));
    }

    @Override
    public void remember(AuthorizationCode code) {
        codes.put(code.code(), code);
    }

    @Override
    public Optional<AuthorizationCode> takeCode(SecretHash code) {
        return Optional.ofNullable(codes.remove(code));
    }

    @Override
    public void save(Grant grant) {
        grantsByRefreshToken.put(grant.refreshToken(), grant);
    }

    @Override
    public Optional<Grant> takeGrant(SecretHash refreshToken) {
        return Optional.ofNullable(grantsByRefreshToken.remove(refreshToken));
    }

    @Override
    public boolean admits(SecretHash accessToken, Instant now) {
        return grantsByRefreshToken.values().stream()
                .anyMatch(grant -> grant.accessToken().equals(accessToken) && now.isBefore(grant.accessExpires()));
    }

    @Override
    public Optional<SecretHash> passphrase() {
        return Optional.ofNullable(passphrase);
    }

    @Override
    public void forgetAll(SecretHash newPassphrase) {
        codes.clear();
        grantsByRefreshToken.clear();
        passphrase = newPassphrase;
    }
}
