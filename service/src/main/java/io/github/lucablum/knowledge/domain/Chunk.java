package io.github.lucablum.knowledge.domain;

import java.util.ArrayList;
import java.util.List;

/** A contiguous section of a Note's content; its ordinal is its position in the current version of the Note. */
public record Chunk(NotePath note, int ordinal, HeadingPath headings, String content) {

    /** Text for the embedding: prefixed with context so the vector knows what the Chunk is about. */
    public String embeddingText() {
        List<String> context = new ArrayList<>();
        note.topic().map(Topic::name).ifPresent(context::add);
        context.add(note.fileName());
        context.addAll(headings.segments());
        return String.join(" > ", context) + "\n\n" + content;
    }
}
