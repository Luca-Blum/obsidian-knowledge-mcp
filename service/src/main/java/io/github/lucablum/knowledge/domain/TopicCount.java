package io.github.lucablum.knowledge.domain;

import org.jmolecules.ddd.annotation.ValueObject;

/** A Topic of the Collection with the number of Notes in it. */
@ValueObject
public record TopicCount(Topic topic, int notes) {}
