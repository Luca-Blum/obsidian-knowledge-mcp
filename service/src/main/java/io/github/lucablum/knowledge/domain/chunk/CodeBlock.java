package io.github.lucablum.knowledge.domain.chunk;

import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/** A fenced code block from its opening fence up to and including the closing one; it is never split. */
@ValueObject
record CodeBlock(List<String> lines) implements MarkdownLine {

    CodeBlock {
        lines = List.copyOf(lines);
    }
}
