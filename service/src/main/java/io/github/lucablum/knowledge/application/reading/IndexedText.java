package io.github.lucablum.knowledge.application.reading;

import io.github.lucablum.knowledge.domain.chunk.section.HeadingPath;
import io.github.lucablum.knowledge.domain.note.NotePath;
import io.github.lucablum.knowledge.domain.reading.NoteReading;
import io.github.lucablum.knowledge.domain.reading.NoteSections;
import io.github.lucablum.knowledge.domain.reading.ReadingRules;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.Application;

/** Reads the text the index stored for a Note, never the Vault, so Claude sees what search sees. */
@Application
public class IndexedText implements ReadNote {

    private final NoteTexts texts;
    private final ReadingRules rules;

    public IndexedText(NoteTexts texts, ReadingRules rules) {
        this.texts = texts;
        this.rules = rules;
    }

    @Override
    public Optional<NoteReading> read(NotePath path) {
        return texts.text(path).map(text -> text.read(rules));
    }

    @Override
    public Optional<NoteSections> read(NotePath path, HeadingPath headings) {
        return texts.text(path).map(text -> new NoteSections(headings, text.sections(headings)));
    }
}
