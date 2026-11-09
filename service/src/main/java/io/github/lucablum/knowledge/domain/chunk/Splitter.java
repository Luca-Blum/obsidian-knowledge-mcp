package io.github.lucablum.knowledge.domain.chunk;

import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.List;
import org.jmolecules.ddd.annotation.Service;

/** Splits a Note into one Chunk per heading section. Frontmatter is not part of any Chunk. */
@Service
public class Splitter {

    public List<Chunk> split(NotePath note, String markdown) {
        ChunkAssembler assembler = new ChunkAssembler(note);
        for (MarkdownLine line : MarkdownLines.of(markdown)) {
            switch (line) {
                case HeadingLine heading -> assembler.startSection(heading);
                case TextLine text -> assembler.addLine(text.value());
            }
        }
        return assembler.finish();
    }
}
