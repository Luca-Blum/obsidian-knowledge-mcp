package io.github.lucablum.knowledge.adapter.ollama;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.EmbeddingPort;
import jakarta.enterprise.context.ApplicationScoped;

/** Embeds text with the configured model served by Ollama. */
@ApplicationScoped
public class OllamaEmbedding implements EmbeddingPort {

    private final EmbeddingModel model;
    private final CollectionConfig.Embedding config;

    public OllamaEmbedding(EmbeddingModel model, CollectionConfig collection) {
        this.model = model;
        this.config = collection.embedding();
    }

    @Override
    public String model() {
        return config.model();
    }

    @Override
    public float[] embed(String text) {
        float[] vector = model.embed(text).content().vector();
        if (vector.length != config.dimension()) {
            throw new IllegalStateException("Model %s returned %d dimensions, but %d are configured"
                    .formatted(config.model(), vector.length, config.dimension()));
        }
        return vector;
    }
}
