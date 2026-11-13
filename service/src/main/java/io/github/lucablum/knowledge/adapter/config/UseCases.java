package io.github.lucablum.knowledge.adapter.config;

import io.github.lucablum.knowledge.application.Embedding;
import io.github.lucablum.knowledge.application.catalog.BrowseCollection;
import io.github.lucablum.knowledge.application.catalog.Catalog;
import io.github.lucablum.knowledge.application.catalog.IndexLookup;
import io.github.lucablum.knowledge.application.indexing.ContentHashReconciliation;
import io.github.lucablum.knowledge.application.indexing.FingerprintReIndex;
import io.github.lucablum.knowledge.application.indexing.IndexBuild;
import io.github.lucablum.knowledge.application.indexing.IndexFingerprint;
import io.github.lucablum.knowledge.application.indexing.IndexVault;
import io.github.lucablum.knowledge.application.indexing.IndexedNotes;
import io.github.lucablum.knowledge.application.indexing.ReIndexCollection;
import io.github.lucablum.knowledge.application.indexing.Vault;
import io.github.lucablum.knowledge.application.link.FollowLinks;
import io.github.lucablum.knowledge.application.link.IndexedLinks;
import io.github.lucablum.knowledge.application.link.QueryTimeResolution;
import io.github.lucablum.knowledge.application.reading.IndexedText;
import io.github.lucablum.knowledge.application.reading.NoteTexts;
import io.github.lucablum.knowledge.application.reading.ReadNote;
import io.github.lucablum.knowledge.application.search.ChunkFullText;
import io.github.lucablum.knowledge.application.search.ChunkVectors;
import io.github.lucablum.knowledge.application.search.HybridSearch;
import io.github.lucablum.knowledge.application.search.SearchChunks;
import io.github.lucablum.knowledge.application.search.SearchSettings;
import io.github.lucablum.knowledge.domain.chunk.ChunkingRules;
import io.github.lucablum.knowledge.domain.chunk.Splitter;
import io.github.lucablum.knowledge.domain.note.NoteLanguage;
import io.github.lucablum.knowledge.domain.note.NoteParser;
import io.github.lucablum.knowledge.domain.reading.ReadingRules;
import io.github.lucablum.knowledge.domain.search.ReciprocalRankFusion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/** Wires the plain-Java use cases to their adapters. */
public class UseCases {

    @Produces
    @ApplicationScoped
    IndexVault indexVault(Vault vault, Embedding embedding, IndexedNotes indexedNotes, CollectionConfig collection) {
        NoteParser parser = new NoteParser(new NoteLanguage(collection.defaultLanguage()));
        Splitter splitter = new Splitter(chunkingRules(collection));
        return new ContentHashReconciliation(vault, parser, splitter, embedding, indexedNotes);
    }

    /** The fingerprint names the model as the Embedding does, which is also the name every Chunk records. */
    @Produces
    @ApplicationScoped
    ReIndexCollection reIndexCollection(
            IndexVault indexVault, IndexBuild build, Embedding embedding, CollectionConfig collection) {
        IndexFingerprint configured =
                new IndexFingerprint(embedding.model(), collection.embedding().dimension(), chunkingRules(collection));
        return new FingerprintReIndex(indexVault, build, configured);
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
    ReadNote readNote(NoteTexts texts, CollectionConfig collection) {
        CollectionConfig.Reading reading = collection.reading();
        return new IndexedText(texts, new ReadingRules(reading.maximumCharacters(), reading.beginningCharacters()));
    }

    @Produces
    @ApplicationScoped
    SearchChunks searchChunks(
            Embedding embedding, ChunkVectors vectors, ChunkFullText fullText, CollectionConfig collection) {
        CollectionConfig.Search search = collection.search();
        CollectionConfig.Search.Fusion fusion = search.fusion();
        return new HybridSearch(embedding, vectors, fullText,
                new ReciprocalRankFusion(fusion.k(), fusion.vectorWeight(), fusion.fullTextWeight()),
                new SearchSettings(search.defaultK(), search.maxK(), search.minimumSimilarity(),
                        search.maxHitsPerNote(), search.candidates()));
    }

    private static ChunkingRules chunkingRules(CollectionConfig collection) {
        CollectionConfig.Chunking chunking = collection.chunking();
        return new ChunkingRules(chunking.headingLevel(), chunking.minimumCharacters(), chunking.maximumCharacters(),
                chunking.overlapCharacters());
    }
}
