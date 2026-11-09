package io.github.lucablum.knowledge.domain.chunk;

import io.github.lucablum.knowledge.domain.note.Note;
import io.github.lucablum.knowledge.domain.note.Topic;
import java.util.ArrayList;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/** A contiguous section of a Note's content; its ordinal is its position in the current version of the Note. */
@ValueObject
public record Chunk(Note note, int ordinal, HeadingPath headings, String content) {

    /**
     * Text for the embedding: prefixed with context so the vector knows what the Chunk is about, and with wikilinks
     * reduced to their display text. Search Hits carry the content as written instead.
     */
    public String embeddingText() {
        List<String> context = new ArrayList<>();
        note.path().topic().map(Topic::name).ifPresent(context::add);
        context.add(note.title());
        context.addAll(headings.segments());
        return Wikilinks.toDisplayText(String.join(" > ", context) + "\n\n" + content);
    }
}
