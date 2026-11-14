package io.github.lucablum.knowledge.domain.access;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * What the consent page hands a Client once the User gave the passphrase: a short-lived code that the Client trades for
 * tokens. Only the Client it was issued to can redeem it, with the same redirect URI and the PKCE verifier.
 */
@ValueObject
public record AuthorizationCode(
        SecretHash code, String clientId, URI redirectUri, CodeChallenge challenge, Instant expires) {

    public static final Duration LIFETIME = Duration.ofMinutes(5);

    public boolean redeemableBy(String clientId, URI redirectUri, String verifier, Instant now) {
        return this.clientId.equals(clientId)
                && this.redirectUri.equals(redirectUri)
                && challenge.isMetBy(verifier)
                && now.isBefore(expires);
    }
}
