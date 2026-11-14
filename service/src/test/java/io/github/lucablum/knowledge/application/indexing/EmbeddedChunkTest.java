package io.github.lucablum.knowledge.application.indexing;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.domain.chunk.Chunk;
import io.github.lucablum.knowledge.domain.markdown.HeadingPath;
import io.github.lucablum.knowledge.domain.note.Note;
import io.github.lucablum.knowledge.domain.note.NoteKind;
import io.github.lucablum.knowledge.domain.note.NoteLanguage;
import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EmbeddedChunkTest {

    private static final Note NOTE = new Note(new NotePath("Running/Intervals.md"), NoteKind.OWN, "Intervals",
            Optional.empty(), Optional.empty(), Optional.empty(), new NoteLanguage("en"));
    private static final Chunk CHUNK = new Chunk(NOTE, 0, new HeadingPath(List.of()), "body");

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
