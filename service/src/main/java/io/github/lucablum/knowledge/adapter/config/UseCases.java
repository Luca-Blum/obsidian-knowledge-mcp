package io.github.lucablum.knowledge.adapter.config;

import io.github.lucablum.knowledge.application.EmbeddingPort;
import io.github.lucablum.knowledge.application.IndexRepository;
import io.github.lucablum.knowledge.application.IndexVault;
import io.github.lucablum.knowledge.application.SearchChunks;
import io.github.lucablum.knowledge.application.VaultReader;
import io.github.lucablum.knowledge.domain.Splitter;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/** Wires the plain-Java use cases to their adapters. */
public class UseCases {

    @Produces
    @Singleton
    IndexVault indexVault(VaultReader vault, EmbeddingPort embedding, IndexRepository repository) {
        return new IndexVault(vault, new Splitter(), embedding, repository);
    }

    @Produces
    @Singleton
    SearchChunks searchChunks(EmbeddingPort embedding, IndexRepository repository) {
        return new SearchChunks(embedding, repository);
    }
}
