package io.github.lucablum.knowledge.domain.note;

import org.jmolecules.ddd.annotation.ValueObject;

/** Whether a Note is a Source Note or an Own Note; there are exactly these two kinds. */
@ValueObject
public enum NoteKind {
    SOURCE("source"),
    OWN("own");

    private final String label;

    NoteKind(String label) {
        this.label = label;
    }

    /** Only {@code type: source} makes a Source Note; any other {@code type}, or none, is an Own Note. */
    public static NoteKind ofType(String type) {
        return SOURCE.label.equals(type) ? SOURCE : OWN;
    }

    /** The name Claude sees, {@code source} or {@code own}. */
    public String label() {
        return label;
    }

    /** The kind whose {@link #label()} is {@code label}. */
    public static NoteKind ofLabel(String label) {
        for (NoteKind kind : values()) {
            if (kind.label.equals(label)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown Note Kind: " + label);
    }
}
