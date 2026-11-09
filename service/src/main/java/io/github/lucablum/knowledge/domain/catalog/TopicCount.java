package io.github.lucablum.knowledge.domain.catalog;

import io.github.lucablum.knowledge.domain.note.Topic;
import org.jmolecules.ddd.annotation.ValueObject;

/** A Topic of the Collection with the number of Notes in it. */
@ValueObject
public record TopicCount(Topic topic, int notes) {}
