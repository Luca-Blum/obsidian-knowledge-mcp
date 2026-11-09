package io.github.lucablum.knowledge.domain.link;

import org.jmolecules.ddd.annotation.ValueObject;

/** How a Link is written: a note link, an embed, or a file link such as the {@code source:} field. */
@ValueObject
public enum LinkKind {
    NOTE("note"),
    EMBED("embed"),
    FILE("file");

    private final String label;

    LinkKind(String label) {
        this.label = label;
    }

    /** The name Claude sees and the index stores. */
    public String label() {
        return label;
    }

    /** The kind whose {@link #label()} is {@code label}. */
    public static LinkKind ofLabel(String label) {
        for (LinkKind kind : values()) {
            if (kind.label.equals(label)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown Link kind: " + label);
    }
}
