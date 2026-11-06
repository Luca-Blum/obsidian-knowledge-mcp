package io.github.lucablum.knowledge.domain.splitting;

import io.github.lucablum.knowledge.domain.Chunk;
import io.github.lucablum.knowledge.domain.NotePath;
import java.util.List;

/** Splits a Note into one Chunk per heading section. Frontmatter is not part of any Chunk. */
public class Splitter {

    public List<Chunk> split(NotePath note, String markdown) {
        ChunkAssembler assembler = new ChunkAssembler(note);
        MarkdownLines.of(markdown).forEach(line -> line.addTo(assembler));
        return assembler.finish();
    }
}
