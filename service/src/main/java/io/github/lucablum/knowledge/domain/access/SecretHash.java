package io.github.lucablum.knowledge.domain.access;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The SHA-256 of a secret, such as a token, as it is stored: whoever reads the store cannot use what they find. The
 * secrets are random and long, so a plain hash without salt is enough.
 */
@ValueObject
public record SecretHash(String value) {

    public static SecretHash of(String secret) {
        return new SecretHash(HexFormat.of().formatHex(sha256(secret)));
    }

    /** Compares in constant time, so the response time does not give away how much of a secret was right. */
    public boolean matches(String secret) {
        return secret != null && MessageDigest.isEqual(
                of(secret).value.getBytes(StandardCharsets.US_ASCII), value.getBytes(StandardCharsets.US_ASCII));
    }

    static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime has SHA-256", e);
        }
    }
}
