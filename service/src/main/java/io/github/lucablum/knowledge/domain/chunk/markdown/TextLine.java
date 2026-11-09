package io.github.lucablum.knowledge.domain.chunk.markdown;

import org.jmolecules.ddd.annotation.ValueObject;

/** Any line that is neither a heading nor part of a code block. */
@ValueObject
public record TextLine(String value) implements MarkdownLine {}
