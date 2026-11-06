package io.github.lucablum.knowledge.domain.splitting;

/** Any line that is not a heading, including lines inside a code fence and the fence lines themselves. */
record TextLine(String value) implements MarkdownLine {

    @Override
    public void addTo(ChunkAssembler assembler) {
        assembler.addLine(value);
    }
}
