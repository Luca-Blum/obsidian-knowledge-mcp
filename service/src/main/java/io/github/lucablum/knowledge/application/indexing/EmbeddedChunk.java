package io.github.lucablum.knowledge.application.indexing;

import io.github.lucablum.knowledge.domain.chunk.Chunk;
import java.util.Arrays;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/** A Chunk together with its vector. */
@ValueObject
public record EmbeddedChunk(Chunk chunk, float[] embedding) {

    /** Arrays are mutable, so the vector is copied in and out to keep this a value. */
    public EmbeddedChunk {
        embedding = embedding.clone();
    }

    @Override
    public float[] embedding() {
        return embedding.clone();
    }

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
