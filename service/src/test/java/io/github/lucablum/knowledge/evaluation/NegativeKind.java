package io.github.lucablum.knowledge.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;

/** Why a Negative Question must not be answered, so that a run shows which kind comes close. */
enum NegativeKind {

    /** A question that belongs to another Collection. */
    @JsonProperty("other-collection") OTHER_COLLECTION,
    /** A question from the Collection's own field that its Vault does not cover. */
    @JsonProperty("near-miss") NEAR_MISS;

    /** The name as it is written in the Evaluation Set. */
    String label() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
