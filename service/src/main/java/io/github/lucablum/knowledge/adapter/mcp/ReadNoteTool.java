package io.github.lucablum.knowledge.adapter.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lucablum.knowledge.application.reading.ReadNote;
import io.github.lucablum.knowledge.domain.chunk.section.HeadingPath;
import io.github.lucablum.knowledge.domain.note.NotePath;
import io.github.lucablum.knowledge.domain.reading.NoteOutline;
import io.github.lucablum.knowledge.domain.reading.NoteReading;
import io.github.lucablum.knowledge.domain.reading.NoteSections;
import io.github.lucablum.knowledge.domain.reading.WholeNote;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

/** The {@code read_note} tool, which lets Claude read a Note, or a section of it, after a promising Search Hit. */
@ApplicationScoped
@PrimaryAdapter
public class ReadNoteTool {

    private final ReadNote readNote;
    private final ObjectMapper json;

    public ReadNoteTool(ReadNote readNote, ObjectMapper json) {
        this.readNote = readNote;
        this.json = json;
    }

    @Tool(name = "read_note",
            description = "Reads one Note of this Collection, usually after a promising Search Hit. A short Note comes "
                    + "back whole, frontmatter included; a long one as its 'outline' (the heading path of every "
                    + "heading) and its 'beginning'. To read one section, pass its heading path as 'heading', exactly "
                    + "as a Search Hit's headingPath or an entry of the outline shows it; every section with that "
                    + "path is returned, including the deeper headings below it. The empty heading path [] is the "
                    + "section above every heading: the whole Note without frontmatter, however long. Only indexed "
                    + "Notes can be read.")
    ToolResponse readNote(
            @ToolArg(description = "The Note's path relative to the Vault root, exactly as a Search Hit's notePath "
                    + "or list_sources shows it.") String path,
            @ToolArg(description = "Optional. The heading path of the section to read, outermost heading first, "
                    + "such as [\"Training\", \"Intervals\"]. Leave it out to read the whole Note, or its outline if it "
                    + "is long.", required = false)
            List<String> heading) {
        if (path == null || path.isBlank()) {
            return ToolResponse.error("path must not be blank");
        }
        NotePath notePath = new NotePath(path);
        if (heading == null) {
            return found(notePath, readNote.read(notePath));
        }
        return found(notePath, readNote.read(notePath, new HeadingPath(heading)));
    }

    private ToolResponse found(NotePath path, Optional<? extends NoteReading> reading) {
        if (reading.isEmpty()) {
            return ToolResponse.error(("Note not found: %s. Only indexed Notes can be read; use the exact notePath "
                    + "of a Search Hit.").formatted(path));
        }
        return switch (reading.get()) {
            case WholeNote whole -> success(new WholeNoteEntry(path.value(), whole.text()));
            case NoteOutline outline -> success(new OutlineEntry(path.value(),
                    outline.headings().stream().map(HeadingPath::segments).toList(), outline.beginning()));
            case NoteSections sections when sections.isEmpty() -> ToolResponse.error(
                    "Note %s has no section with heading path %s. Read it without 'heading' to see its outline."
                            .formatted(path, sections.heading().segments()));
            case NoteSections sections -> success(new SectionsEntry(path.value(), sections.heading().segments(),
                    sections.sections()));
        };
    }

    private ToolResponse success(Object result) {
        try {
            return ToolResponse.success(json.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private record WholeNoteEntry(String notePath, String text) {}

    private record OutlineEntry(String notePath, List<List<String>> outline, String beginning) {}

    private record SectionsEntry(String notePath, List<String> heading, List<String> sections) {}
}
