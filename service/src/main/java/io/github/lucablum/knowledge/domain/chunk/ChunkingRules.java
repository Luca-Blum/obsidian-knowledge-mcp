package io.github.lucablum.knowledge.domain.chunk;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * How Notes become Chunks. Sizes are in characters, so comparing embedding models never changes the Chunks.
 *
 * @param headingLevel the deepest heading level that starts a new section; deeper headings stay inside it
 * @param minimumCharacters a section shorter than this is merged with a neighbour
 * @param maximumCharacters a section longer than this is split at paragraphs; code blocks and tables never are
 * @param overlapCharacters how much of the end of a split piece the next piece repeats
 */
@ValueObject
public record ChunkingRules(int headingLevel, int minimumCharacters, int maximumCharacters, int overlapCharacters) {

    public ChunkingRules {
        if (headingLevel < 1 || headingLevel > 6) {
            throw new IllegalArgumentException("heading level must be 1 to 6: " + headingLevel);
        }
        if (minimumCharacters < 0 || overlapCharacters < 0 || maximumCharacters < minimumCharacters) {
            throw new IllegalArgumentException("chunk sizes need 0 <= minimum <= maximum and overlap >= 0: %d / %d / %d"
                    .formatted(minimumCharacters, maximumCharacters, overlapCharacters));
        }
    }
}
