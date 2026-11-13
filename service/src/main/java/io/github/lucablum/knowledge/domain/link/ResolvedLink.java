package io.github.lucablum.knowledge.domain.link;

import io.github.lucablum.knowledge.domain.note.NotePath;
import org.jmolecules.ddd.annotation.ValueObject;

/** A Link whose target matches exactly one Note. */
@ValueObject
public record ResolvedLink(Link link, NotePath note) {}
