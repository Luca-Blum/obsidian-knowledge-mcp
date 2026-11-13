package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Removing one client's token locks out that client and no other. */
@QuarkusTest
@TestProfile(RemovedClientTokenTest.WithoutClaudeAi.class)
class RemovedClientTokenTest {

    @Test
    void whenConnect_givenTokenOfRemovedClient_thenRejectAsUnauthorized() {
        McpAssured.newStreamableClient()
                .setBearerToken("test-token-claude-ai")
                .setExpectConnectFailure(failure -> assertThat(failure.statusCode()).isEqualTo(401))
                .build()
                .connect();
    }

    @Test
    void whenListTools_givenTokenOfRemainingClient_thenAccept() {
        McpAssured.newStreamableClient()
                .setBearerToken("test-token-claude-code")
                .build()
                .connect()
                .when()
                .toolsList(page -> assertThat(page.findByName("search")).isNotNull())
                .thenAssertResults();
    }

    /** The test profile with the claude.ai client's token removed from the configuration. */
    public static class WithoutClaudeAi implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("collection.auth.tokens.claude-ai", "");
        }
    }
}
