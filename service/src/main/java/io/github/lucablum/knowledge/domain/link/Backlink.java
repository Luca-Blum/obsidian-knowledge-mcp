package io.github.lucablum.knowledge.domain.link;

import io.github.lucablum.knowledge.domain.note.NotePath;
import org.jmolecules.ddd.annotation.ValueObject;

/** A Link written in {@code from} that resolves to the Note being looked at. */
@ValueObject
public record Backlink(NotePath from, Link link) {}
