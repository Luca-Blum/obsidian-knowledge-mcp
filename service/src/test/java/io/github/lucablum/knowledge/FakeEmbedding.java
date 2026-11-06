package io.github.lucablum.knowledge;

import io.github.lucablum.knowledge.application.EmbeddingPort;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.Locale;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Deterministic bag-of-words embedding: every word adds weight to one dimension, so texts sharing words are
 * similar. Tests arrange similarity through the words they choose.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class FakeEmbedding implements EmbeddingPort {

    public static final String MODEL = "fake-bag-of-words";

    @ConfigProperty(name = "collection.embedding.dimension")
    int dimension;

    @Override
    public String model() {
        return MODEL;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty()) {
                vector[Math.floorMod(word.hashCode(), dimension)] += 1;
            }
        }
        return vector;
    }
}
