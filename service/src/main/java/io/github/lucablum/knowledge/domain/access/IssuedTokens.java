package io.github.lucablum.knowledge.domain.access;

import java.time.Duration;
import java.time.Instant;
import org.jmolecules.ddd.annotation.ValueObject;

/** A new Grant together with its tokens in plain text, which only the Client ever sees, and only this once. */
@ValueObject
public record IssuedTokens(String accessToken, String refreshToken, Grant grant) {

    public static IssuedTokens issueTo(String clientId, Instant now) {
        String accessToken = Secrets.generate();
        String refreshToken = Secrets.generate();
        Grant grant = new Grant(clientId,
                SecretHash.of(accessToken), now.plus(Grant.ACCESS_LIFETIME),
                SecretHash.of(refreshToken), now.plus(Grant.REFRESH_LIFETIME));
        return new IssuedTokens(accessToken, refreshToken, grant);
    }

    public Duration expiresIn() {
        return Grant.ACCESS_LIFETIME;
    }

    @Override
    public String toString() {
        return "IssuedTokens[client=%s]".formatted(grant.clientId());
    }
}
