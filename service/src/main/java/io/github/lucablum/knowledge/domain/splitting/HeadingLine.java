package io.github.lucablum.knowledge.domain.splitting;

/** A heading line; it ends the current section and opens the next one. */
record HeadingLine(int level, String text) implements MarkdownLine {

    @Override
    public void addTo(ChunkAssembler assembler) {
        assembler.startSection(this);
    }
}
