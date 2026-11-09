package io.github.lucablum.knowledge.adapter.config;

import io.github.lucablum.knowledge.application.Embedding;
import io.github.lucablum.knowledge.application.catalog.BrowseCollection;
import io.github.lucablum.knowledge.application.catalog.Catalog;
import io.github.lucablum.knowledge.application.catalog.IndexLookup;
import io.github.lucablum.knowledge.application.indexing.IndexVault;
import io.github.lucablum.knowledge.application.indexing.IndexedNotes;
import io.github.lucablum.knowledge.application.indexing.Vault;
import io.github.lucablum.knowledge.application.indexing.VaultIndexer;
import io.github.lucablum.knowledge.application.search.ChunkVectors;
import io.github.lucablum.knowledge.application.search.SearchChunks;
import io.github.lucablum.knowledge.application.search.VectorSearch;
import io.github.lucablum.knowledge.domain.chunk.Splitter;
import io.github.lucablum.knowledge.domain.note.NoteLanguage;
import io.github.lucablum.knowledge.domain.note.NoteParser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/** Wires the plain-Java use cases to their adapters. */
public class UseCases {

    @Produces
    @ApplicationScoped
    IndexVault indexVault(Vault vault, Embedding embedding, IndexedNotes indexedNotes, CollectionConfig collection) {
        NoteParser parser = new NoteParser(new NoteLanguage(collection.defaultLanguage()));
        return new VaultIndexer(vault, parser, new Splitter(), embedding, indexedNotes);
    }

    @Produces
    @ApplicationScoped
    BrowseCollection browseCollection(Catalog catalog) {
        return new IndexLookup(catalog);
    }

    @Produces
    @ApplicationScoped
    SearchChunks searchChunks(Embedding embedding, ChunkVectors vectors, CollectionConfig collection) {
        CollectionConfig.Search search = collection.search();
        return new VectorSearch(embedding, vectors, search.defaultK(), search.maxK());
    }
}
