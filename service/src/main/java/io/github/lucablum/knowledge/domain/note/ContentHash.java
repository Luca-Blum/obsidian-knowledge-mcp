package io.github.lucablum.knowledge.domain.note;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/** The SHA-256 of a Note's text, which tells whether the indexed version of the Note is still current. */
@ValueObject
public record ContentHash(String value) {

    public ContentHash {
        Objects.requireNonNull(value);
    }

    public static ContentHash of(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return new ContentHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform provides SHA-256", e);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
