package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;

/** The two Clients of the test profile, each with its own token, as tests reach the MCP endpoint and /admin. */
final class TestClients {

    static final String FIRST_TOKEN = "test-token-first";

    static final String SECOND_TOKEN = "test-token-second";

    private TestClients() {
    }

    /** Connects as the first Client, as every tool test does. */
    static McpStreamableTestClient connect() {
        return connectWith(FIRST_TOKEN);
    }

    static McpStreamableTestClient connectWith(String token) {
        return McpAssured.newStreamableClient().setBearerToken(token).build().connect();
    }

    /** Connects with {@code token}, or without one if it is null, and expects {@code 401}. */
    static void expectRejected(String token) {
        McpAssured.McpStreamableTestClient.Builder client = McpAssured.newStreamableClient();
        if (token != null) {
            client.setBearerToken(token);
        }
        client.setExpectConnectFailure(failure -> assertThat(failure.statusCode()).isEqualTo(401))
                .build()
                .connect();
    }

    /** The {@code Authorization} header of the first Client, for plain HTTP requests. */
    static String authorization() {
        return "Bearer " + FIRST_TOKEN;
    }
}
