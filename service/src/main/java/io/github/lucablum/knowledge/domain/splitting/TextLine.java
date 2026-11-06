package io.github.lucablum.knowledge.domain.splitting;

import org.jmolecules.ddd.annotation.ValueObject;

/** Any line that is not a heading, including lines inside a code fence and the fence lines themselves. */
@ValueObject
record TextLine(String value) implements MarkdownLine {}
