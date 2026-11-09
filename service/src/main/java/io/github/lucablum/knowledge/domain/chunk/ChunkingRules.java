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
}
