package io.github.lucablum.knowledge.domain.access;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * What the User types on a Collection's consent page to let a Client in. The page is public, so the passphrase must be
 * too long to guess; nothing else stands between the internet and the Collection.
 */
@ValueObject
public record Passphrase(String value) {

    public static final int MINIMUM_LENGTH = 20;

    public Passphrase {
        if (value == null || value.length() < MINIMUM_LENGTH) {
            throw new IllegalArgumentException(("The consent passphrase needs at least %d characters, so it cannot be "
                    + "guessed through the public consent page").formatted(MINIMUM_LENGTH));
        }
    }

    /** Compares in constant time, so the response time does not give away how much of an attempt was right. */
    public boolean admits(String attempt) {
        return attempt != null && MessageDigest.isEqual(
                attempt.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8));
    }

    /** What is stored to notice that the passphrase changed, without storing the passphrase. */
    public SecretHash hash() {
        return SecretHash.of(value);
    }

    @Override
    public String toString() {
        return "Passphrase[hidden]";
    }
}
