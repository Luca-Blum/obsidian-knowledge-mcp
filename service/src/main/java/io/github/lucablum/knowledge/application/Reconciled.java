package io.github.lucablum.knowledge.application;

import org.jmolecules.ddd.annotation.ValueObject;

/** What one Reconciliation did: Notes it indexed because they were new or changed, removed, and left as they were. */
@ValueObject
public record Reconciled(int indexed, int removed, int unchanged) {}
