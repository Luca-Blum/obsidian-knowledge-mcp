package io.github.lucablum.knowledge.domain.link;

import java.util.Objects;
import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * A reference written in a Note, kept as the author wrote it: the target without alias or anchor, and the heading or
 * block the Link points into. Which Note it means is decided only when it is read (ADR 0001).
 */
@ValueObject
public record Link(String target, LinkKind kind, Optional<String> anchor) {

    public Link {
        Objects.requireNonNull(target);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(anchor);
    }

    /** Whether the target is a file rather than a Note: a file link, or an embed of an image, PDF and the like. */
    public boolean pointsToFile() {
        return kind == LinkKind.FILE || Attachments.isAttachment(target);
    }
}
