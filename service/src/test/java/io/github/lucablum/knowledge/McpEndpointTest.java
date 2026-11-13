package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The MCP endpoint as a client reaches it over HTTP: who gets in, and which tools it finds. */
@QuarkusTest
class McpEndpointTest {

    @TestHTTPResource("/admin/re-index")
    URI adminReIndex;

    @Test
    void whenConnect_givenNoToken_thenRejectAsUnauthorized() {
        McpAssured.newStreamableClient()
                .setExpectConnectFailure(failure -> assertThat(failure.statusCode()).isEqualTo(401))
                .build()
                .connect();
    }

    @Test
    void whenConnect_givenWrongToken_thenRejectAsUnauthorized() {
        McpAssured.newStreamableClient()
                .setBearerToken("not-a-client-token")
                .setExpectConnectFailure(failure -> assertThat(failure.statusCode()).isEqualTo(401))
                .build()
                .connect();
    }

    @ParameterizedTest
    @ValueSource(strings = {"test-token-claude-ai", "test-token-claude-code"})
    void whenListTools_givenAnyClientsToken_thenFindEveryTool(String token) {
        McpStreamableTestClient client = McpAssured.newStreamableClient().setBearerToken(token).build().connect();

        client.when()
                .toolsList(page -> assertThat(page.tools()).extracting(McpAssured.ToolInfo::name)
                        .containsExactlyInAnyOrder("search", "read_note", "related", "list_sources", "list_topics"))
                .thenAssertResults();
    }

    @Test
    void whenListTools_givenCollectionDescription_thenSearchToolCarriesIt() {
        McpStreamableTestClient client = McpClients.connect();

        client.when()
                .toolsList(page -> assertThat(page.findByName("search").description())
                        .isEqualTo("Searches the test Collection."))
                .thenAssertResults();
    }

    @Test
    void whenPostAdminReIndex_givenNoToken_thenRejectAsUnauthorized() throws Exception {
        HttpResponse<Void> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(adminReIndex).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(401);
    }
}
