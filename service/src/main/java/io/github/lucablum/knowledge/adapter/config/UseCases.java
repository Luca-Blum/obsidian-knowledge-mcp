package io.github.lucablum.knowledge.adapter.config;

import io.github.lucablum.knowledge.application.EmbeddingPort;
import io.github.lucablum.knowledge.application.IndexRepository;
import io.github.lucablum.knowledge.application.IndexVault;
import io.github.lucablum.knowledge.application.SearchChunks;
import io.github.lucablum.knowledge.application.VaultReader;
import io.github.lucablum.knowledge.domain.Splitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/** Wires the plain-Java use cases to their adapters. */
public class UseCases {

    @Produces
    @ApplicationScoped
    IndexVault indexVault(VaultReader vault, EmbeddingPort embedding, IndexRepository repository) {
        return new IndexVault(vault, new Splitter(), embedding, repository);
    }

    @Produces
    @ApplicationScoped
    SearchChunks searchChunks(EmbeddingPort embedding, IndexRepository repository, CollectionConfig collection) {
        return new SearchChunks(embedding, repository, collection.search().maxK());
    }
}
