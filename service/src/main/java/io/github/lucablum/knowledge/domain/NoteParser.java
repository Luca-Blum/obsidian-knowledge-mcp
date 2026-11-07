package io.github.lucablum.knowledge.domain;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jmolecules.ddd.annotation.Service;

/**
 * Derives a Note's Note Kind, Title, dates, author and Note Language from its frontmatter. Topic comes from the
 * folder alone, so a {@code topic} in the frontmatter is ignored. What looks wrong is reported, never corrected.
 */
@Service
public class NoteParser {

    /** Where Source Notes are filed by convention; the folder itself makes no Note a Source Note. */
    private static final String SOURCE_NOTES_FOLDER = "Source-Notes";

    private final NoteLanguage defaultLanguage;

    public NoteParser(NoteLanguage defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public ParsedNote parse(NotePath path, String markdown) {
        List<String> warnings = new ArrayList<>();
        Frontmatter frontmatter = frontmatter(markdown, warnings);
        NoteKind kind = NoteKind.ofType(frontmatter.get("type").orElse(null));
        if (kind == NoteKind.OWN && path.isInFolder(SOURCE_NOTES_FOLDER)) {
            warnings.add("is in %s/ but its frontmatter lacks type: source".formatted(SOURCE_NOTES_FOLDER));
        }
        Note note = new Note(
                path,
                kind,
                frontmatter.get("title").orElse(path.fileName()),
                frontmatter.get("imported").flatMap(value -> importedDate(value, warnings)),
                frontmatter.get("published").flatMap(value -> publishedDate(value, warnings)),
                frontmatter.get("author"),
                frontmatter.get("lang").flatMap(value -> language(value, warnings)).orElse(defaultLanguage));
        return new ParsedNote(note, warnings);
    }

    /** Invalid frontmatter counts as none, so the Note is still indexed with its fallbacks. */
    private static Frontmatter frontmatter(String markdown, List<String> warnings) {
        try {
            return Frontmatter.of(markdown);
        } catch (IllegalArgumentException e) {
            warnings.add(e.getMessage());
            return new Frontmatter(Map.of());
        }
    }

    private static Optional<LocalDate> importedDate(String value, List<String> warnings) {
        try {
            return Optional.of(LocalDate.parse(value));
        } catch (DateTimeException e) {
            warnings.add("imported is not a date (YYYY-MM-DD): " + value);
            return Optional.empty();
        }
    }

    private static Optional<PublishedDate> publishedDate(String value, List<String> warnings) {
        Optional<PublishedDate> published = PublishedDate.parse(value);
        if (published.isEmpty()) {
            warnings.add("published is not a year, year-month or date: " + value);
        }
        return published;
    }

    private Optional<NoteLanguage> language(String value, List<String> warnings) {
        Optional<NoteLanguage> language = NoteLanguage.parse(value);
        if (language.isEmpty()) {
            warnings.add("lang is unknown, using %s: %s".formatted(defaultLanguage, value));
        }
        return language;
    }
}
