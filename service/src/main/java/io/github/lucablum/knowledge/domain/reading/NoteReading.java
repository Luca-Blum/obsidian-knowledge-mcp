package io.github.lucablum.knowledge.domain.reading;

import org.jmolecules.ddd.annotation.ValueObject;

/** What Claude gets back when it reads a Note: the whole Note, the outline of a long one, or some of its sections. */
@ValueObject
public sealed interface NoteReading permits WholeNote, NoteOutline, NoteSections {
}
