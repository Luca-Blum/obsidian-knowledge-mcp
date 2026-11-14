package io.github.lucablum.knowledge.domain.access;

import java.time.Duration;
import java.time.Instant;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The access a Client holds after the User consented: an access token for its requests and a refresh token to get the
 * next pair. Only their hashes are kept. A refresh replaces the whole Grant, so each refresh token works once.
 */
@ValueObject
public record Grant(
        String clientId, SecretHash accessToken, Instant accessExpires, SecretHash refreshToken, Instant refreshExpires) {

    public static final Duration ACCESS_LIFETIME = Duration.ofHours(1);

    /** A Client that goes unused this long has to pass the consent page again. */
    public static final Duration REFRESH_LIFETIME = Duration.ofDays(30);

    public boolean refreshableAt(Instant now) {
        return now.isBefore(refreshExpires);
    }
}
