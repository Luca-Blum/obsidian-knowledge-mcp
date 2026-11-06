package io.github.lucablum.knowledge.application;

import io.github.lucablum.knowledge.domain.Chunk;
import java.util.Arrays;
import java.util.Objects;

/** A Chunk together with its vector. */
public record EmbeddedChunk(Chunk chunk, float[] embedding) {

    /** Arrays compare by identity by default, so equality is defined over their contents. */
    @Override
    public boolean equals(Object other) {
        return other instanceof EmbeddedChunk(Chunk otherChunk, float[] otherEmbedding)
                && chunk.equals(otherChunk)
                && Arrays.equals(embedding, otherEmbedding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunk, Arrays.hashCode(embedding));
    }

    @Override
    public String toString() {
        return "EmbeddedChunk[chunk=" + chunk + ", embedding=" + Arrays.toString(embedding) + "]";
    }
}
