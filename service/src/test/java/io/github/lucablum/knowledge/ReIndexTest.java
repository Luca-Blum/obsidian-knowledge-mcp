package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.indexing.EmbeddedChunk;
import io.github.lucablum.knowledge.application.indexing.IndexBuild;
import io.github.lucablum.knowledge.application.indexing.IndexFingerprint;
import io.github.lucablum.knowledge.application.indexing.IndexVault;
import io.github.lucablum.knowledge.application.indexing.IndexedNotes;
import io.github.lucablum.knowledge.application.indexing.ReIndexCollection;
import io.github.lucablum.knowledge.application.search.SearchChunks;
import io.github.lucablum.knowledge.application.search.SearchRequest;
import io.github.lucablum.knowledge.domain.chunk.Chunk;
import io.github.lucablum.knowledge.domain.chunk.ChunkingRules;
import io.github.lucablum.knowledge.domain.chunk.section.HeadingPath;
import io.github.lucablum.knowledge.domain.note.ContentHash;
import io.github.lucablum.knowledge.domain.note.Note;
import io.github.lucablum.knowledge.domain.note.NoteKind;
import io.github.lucablum.knowledge.domain.note.NoteLanguage;
import io.github.lucablum.knowledge.domain.note.NotePath;
import io.github.lucablum.knowledge.domain.search.SearchHit;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A changed model, dimension or chunking rule rebuilds the whole index, so no Chunk keeps what the old one made. */
@QuarkusTest
class ReIndexTest {

    private static final String OLD_MODEL = "old-model";

    @Inject
    TestVault vault;

    @Inject
    IndexVault indexVault;

    @Inject
    ReIndexCollection reIndex;

    @Inject
    IndexBuild build;

    @Inject
    IndexedNotes indexedNotes;

    @Inject
    SearchChunks search;

    @Inject
    TestIndex index;

    @Inject
    CollectionConfig collection;

    @BeforeEach
    void emptyVaultAndIndex() {
        vault.clear();
        indexedNotes.remove(indexedNotes.contentHashes().keySet());
    }

    /** A failed test must not leave vectors of another dimension behind for the next one. */
    @AfterEach
    void configuredDimension() {
        build.startOver(collection.embedding().dimension());
    }

    @Test
    void whenReIndex_givenChunksOfAnOldModel_thenEveryChunkRecordsTheNewModelAndNoneTheOld() throws SQLException {
        vault.write("Running/Intervals.md", "## Short\nInterval training improves speed\n\n## Long\nLong runs");
        vault.write("Running/Tempo.md", "Tempo runs at threshold pace");
        indexVault.run();
        index.markEveryChunkAsEmbeddedBy(OLD_MODEL);

        reIndex.reIndex();

        assertThat(index.embeddingModelOfEveryChunk())
                .hasSize(3)
                .containsOnly(FakeEmbedding.MODEL);
    }

    @Test
    void whenReIndexIfOutdated_givenIndexOfAnotherModelAndDimension_thenRebuildItSoSearchFindsTheNotes() {
        String text = "Interval training improves speed";
        vault.write("Running/Intervals.md", text);
        indexedAsByAnOldModelWithThreeDimensions("Running/Intervals.md", text);

        assertThat(reIndex.reIndexIfOutdated()).isPresent();

        assertThat(search.search(SearchRequest.of("interval training"))).extracting(SearchHit::notePath)
                .containsExactly("Running/Intervals.md");
        assertThat(build.fingerprint()).hasValueSatisfying(fingerprint -> {
            assertThat(fingerprint.embeddingModel()).isEqualTo(FakeEmbedding.MODEL);
            assertThat(fingerprint.dimension()).isEqualTo(collection.embedding().dimension());
        });
    }

    @Test
    void whenReIndexIfOutdated_givenReIndexCompletedWithTheConfiguration_thenLeaveTheIndexAlone() {
        vault.write("Running/Tempo.md", "Tempo runs at threshold pace");
        reIndex.reIndex();

        assertThat(reIndex.reIndexIfOutdated()).isEmpty();
    }

    /** The content hash matches the Vault, so only the fingerprint can tell that the Note needs indexing again. */
    private void indexedAsByAnOldModelWithThreeDimensions(String path, String text) {
        build.startOver(3);
        Note note = new Note(new NotePath(path), NoteKind.OWN, path, Optional.empty(), Optional.empty(),
                Optional.empty(), new NoteLanguage("en"));
        Chunk chunk = new Chunk(note, 0, new HeadingPath(List.of()), text);
        indexedNotes.replace(note, text, ContentHash.of(text), List.of(),
                List.of(new EmbeddedChunk(chunk, new float[] {1, 0, 0})), OLD_MODEL);
        build.complete(new IndexFingerprint(OLD_MODEL, 3, new ChunkingRules(3, 0, 2000, 100)));
    }
}
