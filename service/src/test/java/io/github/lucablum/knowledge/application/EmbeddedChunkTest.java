package io.github.lucablum.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.domain.Chunk;
import io.github.lucablum.knowledge.domain.HeadingPath;
import io.github.lucablum.knowledge.domain.NotePath;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddedChunkTest {

    private static final Chunk CHUNK = new Chunk(new NotePath("Running/Intervals.md"), 0, new HeadingPath(List.of()), "body");

    @Test
    void whenChangeVector_givenArrayPassedToConstructor_thenEmbeddedChunkIsUnchanged() {
        float[] vector = {1f, 2f};
        EmbeddedChunk embedded = new EmbeddedChunk(CHUNK, vector);

        vector[0] = 9f;

        assertThat(embedded.embedding()).containsExactly(1f, 2f);
    }

    @Test
    void whenChangeVector_givenArrayReadFromEmbeddedChunk_thenEmbeddedChunkIsUnchanged() {
        EmbeddedChunk embedded = new EmbeddedChunk(CHUNK, new float[] {1f, 2f});

        embedded.embedding()[0] = 9f;

        assertThat(embedded.embedding()).containsExactly(1f, 2f);
    }
}
