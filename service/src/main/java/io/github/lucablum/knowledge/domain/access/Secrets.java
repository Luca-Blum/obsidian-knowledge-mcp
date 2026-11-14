package io.github.lucablum.knowledge.domain.access;

import java.security.SecureRandom;
import java.util.Base64;

/** Random secrets for tokens, codes and client secrets: 256 bits, URL-safe so they fit into a redirect as they are. */
public final class Secrets {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int BYTES = 32;

    private Secrets() {
    }

    public static String generate() {
        byte[] secret = new byte[BYTES];
        RANDOM.nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }
}
