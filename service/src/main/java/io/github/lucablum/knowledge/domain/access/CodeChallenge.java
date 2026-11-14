package io.github.lucablum.knowledge.domain.access;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The PKCE challenge (method S256) a Client sends when it asks for an authorization code. Only whoever holds the
 * matching verifier can redeem the code, so a code caught on its way back is worthless.
 */
@ValueObject
public record CodeChallenge(String value) {

    public boolean isMetBy(String verifier) {
        if (verifier == null) {
            return false;
        }
        String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(SecretHash.sha256(verifier));
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), value.getBytes(StandardCharsets.US_ASCII));
    }
}
