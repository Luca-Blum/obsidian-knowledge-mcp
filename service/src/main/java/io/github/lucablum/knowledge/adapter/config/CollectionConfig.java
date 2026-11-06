package io.github.lucablum.knowledge.adapter.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.nio.file.Path;

/** Everything that makes this instance serve one particular Collection. */
@ConfigMapping(prefix = "collection")
public interface CollectionConfig {

    String name();

    Path vaultPath();

    /** The Postgres schema that holds this Collection's index. */
    String schema();

    /** The HTTP port of this Collection's MCP endpoint. */
    int mcpPort();

    @WithDefault("true")
    boolean indexOnStartup();

    Embedding embedding();

    Search search();

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
    }
}
