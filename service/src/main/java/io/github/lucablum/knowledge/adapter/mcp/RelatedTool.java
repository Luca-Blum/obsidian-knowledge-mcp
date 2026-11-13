package io.github.lucablum.knowledge.adapter.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lucablum.knowledge.application.link.FollowLinks;
import io.github.lucablum.knowledge.domain.link.AmbiguousLink;
import io.github.lucablum.knowledge.domain.link.Backlink;
import io.github.lucablum.knowledge.domain.link.Link;
import io.github.lucablum.knowledge.domain.link.Related;
import io.github.lucablum.knowledge.domain.link.ResolvedLink;
import io.github.lucablum.knowledge.domain.note.NotePath;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolResponse;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

/** The {@code related} tool, which lets the AI Agent follow the Links between Notes in both directions. */
@ApplicationScoped
@PrimaryAdapter
public class RelatedTool {

    private final FollowLinks followLinks;
    private final ObjectMapper json;

    public RelatedTool(FollowLinks followLinks, ObjectMapper json) {
        this.followLinks = followLinks;
        this.json = json;
    }

    @Tool(name = "related",
            description = "Shows how one Note is linked: 'links' to other Notes, 'backlinks' from other Notes, "
                    + "'files' it links to (such as the PDF of a Source Note), 'unresolved' Links that match no Note, "
                    + "and 'ambiguous' Links that match several Notes, with all 'candidates'. 'anchor' is the heading, "
                    + "or the block after '^', a Link points into. Read a linked Note with read_note.")
    ToolResponse related(@ToolArg(description = "The Note path, exactly as a Search Hit shows it.") String path) {
        if (path == null || path.isBlank()) {
            return ToolResponse.error("path must not be blank");
        }
        Optional<Related> related = followLinks.related(new NotePath(path));
        if (related.isEmpty()) {
            return ToolResponse.error("Note not found: " + path);
        }
        try {
            return ToolResponse.success(json.writeValueAsString(RelatedEntry.of(related.get())));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private record RelatedEntry(
            List<LinkEntry> links,
            List<LinkEntry> backlinks,
            List<LinkEntry> files,
            List<LinkEntry> unresolved,
            List<LinkEntry> ambiguous) {

        static RelatedEntry of(Related related) {
            return new RelatedEntry(
                    related.links().stream().map(LinkEntry::of).toList(),
                    related.backlinks().stream().map(LinkEntry::of).toList(),
                    related.files().stream().map(LinkEntry::of).toList(),
                    related.unresolved().stream().map(LinkEntry::of).toList(),
                    related.ambiguous().stream().map(LinkEntry::of).toList());
        }
    }

    /**
     * A Link as the AI Agent reads it, with its target as written. {@code notePath} is the Note it resolves to, or for
     * a Backlink the Note it is written in; an anchor or a field that does not apply is left out.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record LinkEntry(String notePath, String target, String kind, String anchor, List<String> candidates) {

        static LinkEntry of(Link link) {
            return of(null, link, null);
        }

        static LinkEntry of(ResolvedLink resolved) {
            return of(resolved.note(), resolved.link(), null);
        }

        static LinkEntry of(Backlink backlink) {
            return of(backlink.from(), backlink.link(), null);
        }

        static LinkEntry of(AmbiguousLink ambiguous) {
            return of(null, ambiguous.link(), ambiguous.candidates().stream().map(NotePath::value).toList());
        }

        private static LinkEntry of(NotePath note, Link link, List<String> candidates) {
            return new LinkEntry(note == null ? null : note.value(), link.target(), link.kind().label(),
                    link.anchor().orElse(null), candidates);
        }
    }
}
