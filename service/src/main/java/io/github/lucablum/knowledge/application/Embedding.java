package io.github.lucablum.knowledge.application;

import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Turns text into a vector with the configured embedding model. */
@SecondaryPort
public interface Embedding {

    /** The name of the model that produces the vectors, recorded with every Chunk. */
    String model();

    float[] embed(String text);
}
