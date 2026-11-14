package io.github.lucablum.knowledge.adapter.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.runtime.configuration.DurationConverter;
import io.smallrye.config.EnvConfigSource;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The client tokens come from the environment as Docker Compose sets them, an emptied one included. */
class CollectionConfigTest {

    /** The ordinal Quarkus gives environment variables, so they win over the properties below them. */
    private static final int ENVIRONMENT = 300;

    private static final int BELOW_ENVIRONMENT = 100;

    /** What {@code CollectionConfig} needs besides the tokens; values do not matter here. */
    private static final Map<String, String> REQUIRED_PROPERTIES = Map.of(
            "collection.name", "engineering",
            "collection.vault-path", "/vault",
            "collection.schema", "engineering",
            "collection.mcp-port", "8081",
            "collection.default-language", "en",
            "collection.embedding.model", "bge-m3",
            "collection.embedding.dimension", "1024",
            "collection.search.tool-description", "Searches.",
            "collection.search.minimum-similarity", "0.5");

    @Test
    void whenMapTokens_givenOneClientTokenEmptiedInTheEnvironment_thenThatClientAloneHasNoToken() {
        CollectionConfig collection = configFromEnvironment(Map.of(
                "COLLECTION_AUTH_TOKENS_CLAUDE_AI", "",
                "COLLECTION_AUTH_TOKENS_CLAUDE_CODE", "code-token"));

        assertThat(collection.auth().tokens()).containsOnly(
                Map.entry("claude.ai", Optional.empty()),
                Map.entry("claude.code", Optional.of("code-token")));
    }

    /** Reads the mapping as Quarkus does, from {@code environment} over the required values. */
    private static CollectionConfig configFromEnvironment(Map<String, String> environment) {
        return new SmallRyeConfigBuilder()
                .withConverter(Duration.class, BELOW_ENVIRONMENT, new DurationConverter())
                .withSources(new PropertiesConfigSource(REQUIRED_PROPERTIES, "required", BELOW_ENVIRONMENT))
                .withSources(new EnvConfigSource(environment, ENVIRONMENT))
                .withMapping(CollectionConfig.class)
                .build()
                .getConfigMapping(CollectionConfig.class);
    }
}
