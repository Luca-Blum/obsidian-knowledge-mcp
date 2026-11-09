package io.github.lucablum.knowledge.adapter.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.nio.file.Path;
import java.time.Duration;

/** Everything that makes this instance serve one particular Collection. */
@ConfigMapping(prefix = "collection")
public interface CollectionConfig {

    String name();

    Path vaultPath();

    /** The Postgres schema that holds this Collection's index. */
    String schema();

    /** The HTTP port of this Collection's MCP endpoint. */
    int mcpPort();

    Reconciliation reconciliation();

    /** The Note Language of every Note whose frontmatter has no known {@code lang}. */
    String defaultLanguage();

    Embedding embedding();

    Search search();

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

    interface Search {

        /** The description Claude reads for the {@code search} tool. */
        String toolDescription();

        @WithDefault("5")
        int defaultK();

        /** Caps {@code k}, so one call cannot pull the whole Collection into Claude's context. */
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

        /** How many of the closest Chunks a search looks at; {@code moreInNote} counts only among them. */
        @WithDefault("50")
        int candidates();
    }
}
