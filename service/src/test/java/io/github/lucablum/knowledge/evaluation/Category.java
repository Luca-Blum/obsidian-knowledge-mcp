package io.github.lucablum.knowledge.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;

/**
 * The shape of a question in the Evaluation Set, so that a run shows where a model wins or loses, not only one
 * average. Each question has exactly one; where shapes overlap, the harder one is chosen.
 */
enum Category {

    /** Asks what a named concept is. */
    @JsonProperty("definition") DEFINITION,
    /** Describes a concept without naming it. */
    @JsonProperty("paraphrase") PARAPHRASE,
    /** Describes a situation in several sentences and asks what to do. */
    @JsonProperty("scenario") SCENARIO,
    /** Asks how two or more concepts differ or relate. */
    @JsonProperty("comparison") COMPARISON,
    /** Needs several Notes to be answered. */
    @JsonProperty("multi-note") MULTI_NOTE,
    /** Is asked in another language than the Note that answers it. */
    @JsonProperty("cross-language") CROSS_LANGUAGE,
    /** Is answered by an Own Note rather than a Source Note. */
    @JsonProperty("own-note") OWN_NOTE;

    /** The name as it is written in the Evaluation Set. */
    String label() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
