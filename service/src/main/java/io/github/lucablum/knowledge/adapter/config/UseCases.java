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
import jakarta.inject.Singleton;

/**
 * Wires the plain-Java use cases to their adapters. Reconciliation and the Re-Index are one {@link FingerprintReIndex},
 * so that they share its lock; it is built once and produced as each port on its own, so a test can replace either.
 */
@Singleton
public class UseCases {

    private final FingerprintReIndex fingerprintReIndex;

    /** The fingerprint names the model as the Embedding does, which is also the name every Chunk records. */
    UseCases(Vault vault, Embedding embedding, IndexedNotes indexedNotes, IndexBuild build, CollectionConfig collection) {
        ChunkingRules chunking = chunkingRules(collection);
        NoteParser parser = new NoteParser(new NoteLanguage(collection.defaultLanguage()));
        IndexVault reconciliation =
                new ContentHashReconciliation(vault, parser, new Splitter(chunking), embedding, indexedNotes);
        IndexFingerprint configured =
                new IndexFingerprint(embedding.model(), collection.embedding().dimension(), chunking);
        fingerprintReIndex = new FingerprintReIndex(reconciliation, build, configured);
    }

    @Produces
    @ApplicationScoped
    IndexVault indexVault() {
        return fingerprintReIndex;
    }

    @Produces
    @ApplicationScoped
    ReIndexCollection reIndexCollection() {
        return fingerprintReIndex;
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
    SearchChunks searchChunks(Embedding embedding, ChunkVectors vectors, ChunkFullText fullText,
            ReIndexCollection reIndex, CollectionConfig collection) {
        CollectionConfig.Search search = collection.search();
        CollectionConfig.Search.Fusion fusion = search.fusion();
        return new HybridSearch(embedding, vectors, fullText, reIndex,
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
