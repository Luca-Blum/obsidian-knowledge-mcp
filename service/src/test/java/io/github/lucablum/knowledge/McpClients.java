package io.github.lucablum.knowledge;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;

/** MCP clients connected with a client token of the test profile, as every tool test needs one. */
final class McpClients {

    /** A client token from the test profile; the AI Agent's client would send its own. */
    static final String TOKEN = "test-token-claude-code";

    private McpClients() {
    }

    static McpStreamableTestClient connect() {
        return McpAssured.newStreamableClient().setBearerToken(TOKEN).build().connect();
    }
}
