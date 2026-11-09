package io.github.lucablum.knowledge.adapter.config;

import io.github.lucablum.knowledge.application.BrowseCollection;
import io.github.lucablum.knowledge.application.Embedding;
import io.github.lucablum.knowledge.application.Index;
import io.github.lucablum.knowledge.application.IndexLookup;
import io.github.lucablum.knowledge.application.IndexVault;
import io.github.lucablum.knowledge.application.SearchChunks;
import io.github.lucablum.knowledge.application.Vault;
import io.github.lucablum.knowledge.application.VaultIndexer;
import io.github.lucablum.knowledge.application.VectorSearch;
import io.github.lucablum.knowledge.domain.NoteLanguage;
import io.github.lucablum.knowledge.domain.NoteParser;
import io.github.lucablum.knowledge.domain.splitting.Splitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/** Wires the plain-Java use cases to their adapters. */
public class UseCases {

    @Produces
    @ApplicationScoped
    IndexVault indexVault(Vault vault, Embedding embedding, Index index, CollectionConfig collection) {
        NoteParser parser = new NoteParser(new NoteLanguage(collection.defaultLanguage()));
        return new VaultIndexer(vault, parser, new Splitter(), embedding, index);
    }

    @Produces
    @ApplicationScoped
    BrowseCollection browseCollection(Index index) {
        return new IndexLookup(index);
    }

    @Produces
    @ApplicationScoped
    SearchChunks searchChunks(Embedding embedding, Index index, CollectionConfig collection) {
        CollectionConfig.Search search = collection.search();
        return new VectorSearch(embedding, index, search.defaultK(), search.maxK());
    }
}
