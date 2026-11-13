package io.github.lucablum.knowledge.domain.reading;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * How much of a Note the AI Agent reads at once. Sizes are in characters, frontmatter included.
 *
 * @param maximumCharacters a Note up to this long is returned in full, a longer one as its outline and beginning
 * @param beginningCharacters how much of the start of a long Note comes with its outline
 */
@ValueObject
public record ReadingRules(int maximumCharacters, int beginningCharacters) {

    public ReadingRules {
        if (beginningCharacters < 0 || maximumCharacters < beginningCharacters) {
            throw new IllegalArgumentException("reading sizes need 0 <= beginning <= maximum: %d / %d"
                    .formatted(beginningCharacters, maximumCharacters));
        }
    }
}
