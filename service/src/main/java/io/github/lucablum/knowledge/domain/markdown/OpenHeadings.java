package io.github.lucablum.knowledge.domain.markdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * The headings above the current line, outermost first. Chunks and reading both take heading paths from here, so a
 * heading path a Search Hit shows is the one reading a Note finds.
 */
@ValueObject
public record OpenHeadings(List<HeadingLine> headings) {

    public static final OpenHeadings NONE = new OpenHeadings(List.of());

    public OpenHeadings {
        headings = List.copyOf(headings);
    }

    /** Opens {@code heading}, which closes every open heading of the same or a deeper level. */
    public OpenHeadings open(HeadingLine heading) {
        List<HeadingLine> open = new ArrayList<>(headings);
        while (!open.isEmpty() && open.getLast().level() >= heading.level()) {
            open.removeLast();
        }
        open.add(heading);
        return new OpenHeadings(open);
    }

    public HeadingPath path() {
        return new HeadingPath(headings.stream().map(HeadingLine::text).toList());
    }

    /** The innermost open heading, if any. */
    public Optional<HeadingLine> last() {
        return headings.isEmpty() ? Optional.empty() : Optional.of(headings.getLast());
    }
}
