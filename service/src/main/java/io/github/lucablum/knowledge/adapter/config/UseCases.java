package io.github.lucablum.knowledge.adapter.config;

import io.github.lucablum.knowledge.application.Embedding;
import io.github.lucablum.knowledge.application.catalog.BrowseCollection;
import io.github.lucablum.knowledge.application.catalog.Catalog;
import io.github.lucablum.knowledge.application.catalog.IndexLookup;
import io.github.lucablum.knowledge.application.indexing.ContentHashReconciliation;
import io.github.lucablum.knowledge.application.indexing.IndexVault;
import io.github.lucablum.knowledge.application.indexing.IndexedNotes;
import io.github.lucablum.knowledge.application.indexing.Vault;
import io.github.lucablum.knowledge.application.link.FollowLinks;
import io.github.lucablum.knowledge.application.link.IndexedLinks;
import io.github.lucablum.knowledge.application.link.QueryTimeResolution;
import io.github.lucablum.knowledge.application.search.ChunkVectors;
import io.github.lucablum.knowledge.application.search.SearchChunks;
import io.github.lucablum.knowledge.application.search.SearchSettings;
import io.github.lucablum.knowledge.application.search.VectorSearch;
import io.github.lucablum.knowledge.domain.chunk.ChunkingRules;
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
        CollectionConfig.Chunking chunking = collection.chunking();
        Splitter splitter = new Splitter(new ChunkingRules(chunking.headingLevel(), chunking.minimumCharacters(),
                chunking.maximumCharacters(), chunking.overlapCharacters()));
        return new ContentHashReconciliation(vault, parser, splitter, embedding, indexedNotes);
    }

    @Produces
    @ApplicationScoped
    BrowseCollection browseCollection(Catalog catalog) {
        return new IndexLookup(catalog);
    }

    @Produces
    @ApplicationScoped
    FollowLinks followLinks(IndexedLinks indexedLinks) {
        return new QueryTimeResolution(indexedLinks);
    }

    @Produces
    @ApplicationScoped
    SearchChunks searchChunks(Embedding embedding, ChunkVectors vectors, CollectionConfig collection) {
        CollectionConfig.Search search = collection.search();
        return new VectorSearch(embedding, vectors, new SearchSettings(search.defaultK(), search.maxK(),
                search.minimumSimilarity(), search.maxHitsPerNote(), search.candidates()));
    }
}
