package io.github.lucablum.knowledge.application;

/** Turns text into a vector with the configured embedding model. */
public interface EmbeddingPort {

    /** The name of the model that produces the vectors, recorded with every Chunk. */
    String model();

    float[] embed(String text);
}
