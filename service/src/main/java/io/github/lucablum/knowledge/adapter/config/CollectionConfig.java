package io.github.lucablum.knowledge.adapter.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** Everything that makes this instance serve one particular Collection. */
@ConfigMapping(prefix = "collection")
public interface CollectionConfig {

    String name();

    Path vaultPath();

    /** The Postgres schema that holds this Collection's index. */
    String schema();

    /** The HTTP port of this Collection's MCP endpoint. */
    int mcpPort();

    Auth auth();

    Reconciliation reconciliation();

    /** The Note Language of every Note whose frontmatter has no known {@code lang}. */
    String defaultLanguage();

    Embedding embedding();

    Chunking chunking();

    Search search();

    Reading reading();

    interface Auth {

        /**
         * The bearer token of every client allowed to reach this Collection, keyed by the client's name. Each client
         * has its own, so removing one token locks out that client alone; an empty token counts as removed.
         */
        Map<String, Optional<String>> tokens();
    }

    interface Reconciliation {

        /** Off only in tests that run Reconciliation themselves. */
        @WithDefault("true")
        boolean enabled();

        /** How often the Vault is compared with the index, whether or not a file change was noticed. */
        @WithDefault("5m")
        Duration interval();

        /** How long the Vault must stay quiet after a file change, so a sync in progress is picked up in one run. */
        @WithDefault("2s")
        Duration quietPeriod();
    }

    interface Embedding {

        String model();

        int dimension();
    }

    /** How Notes become Chunks; sizes are in characters, so comparing models never changes the Chunks. */
    interface Chunking {

        /** The deepest heading level that starts a new Chunk: H2 and H3 by default, as H1 is usually the Title. */
        @WithDefault("3")
        int headingLevel();

        /** A section shorter than this is merged with a neighbour. */
        @WithDefault("200")
        int minimumCharacters();

        /** A section longer than this is split at paragraphs; code blocks and tables never are. */
        @WithDefault("2000")
        int maximumCharacters();

        /** How much of the end of a split piece the next piece repeats. */
        @WithDefault("100")
        int overlapCharacters();
    }

    interface Search {

        /** The description the AI Agent reads for the {@code search} tool. */
        String toolDescription();

        @WithDefault("5")
        int defaultK();

        /** Caps {@code k}, so one call cannot pull the whole Collection into the AI Agent's context. */
        @WithDefault("20")
        int maxK();

        /**
         * The cosine similarity a Chunk must reach to count as relevant at all. It belongs to the embedding model
         * and is calibrated per Collection with Negative Questions.
         */
        double minimumSimilarity();

        /** Caps the Search Hits of one Note, so one long Note cannot take every slot. */
        @WithDefault("2")
        int maxHitsPerNote();

        /**
         * How many candidates each list contributes to the fusion: the closest Chunks of the vector search and the best
         * ranked of the full-text search. {@code moreInNote} counts only among them.
         */
        @WithDefault("50")
        int candidates();

        Fusion fusion();

        /** How Reciprocal Rank Fusion merges the vector and the full-text list: {@code weight / (k + rank)}. */
        interface Fusion {

            /** Flattens the difference between the top ranks and the ones below; 60 is the usual choice. */
            @WithDefault("60")
            int k();

            @WithDefault("1.0")
            double vectorWeight();

            @WithDefault("1.0")
            double fullTextWeight();
        }
    }

    /** How much of a Note {@code read_note} returns at once; sizes are in characters, frontmatter included. */
    interface Reading {

        /** A Note up to this long is returned in full, a longer one as the outline of its headings and its beginning. */
        @WithDefault("8000")
        int maximumCharacters();

        /** How much of the start of a long Note comes with its outline. */
        @WithDefault("2000")
        int beginningCharacters();
    }
}
