package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.MultiMap;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The MCP endpoint as a Client reaches it over HTTP: who gets in, and which tools it finds. */
@QuarkusTest
class McpEndpointTest {

    @TestHTTPResource("/admin/re-index")
    URI adminReIndex;

    @TestHTTPResource("/q/health/ready")
    URI readiness;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void whenConnect_givenNoToken_thenRejectAsUnauthorized() {
        TestClients.expectRejected(null);
    }

    @Test
    void whenConnect_givenWrongToken_thenRejectAsUnauthorized() {
        TestClients.expectRejected("not-a-client-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {TestClients.FIRST_TOKEN, TestClients.SECOND_TOKEN})
    void whenListTools_givenAnyClientsToken_thenFindEveryTool(String token) {
        TestClients.connectWith(token)
                .when()
                .toolsList(page -> assertThat(page.tools()).extracting(McpAssured.ToolInfo::name)
                        .containsExactlyInAnyOrder("search", "read_note", "related", "list_sources", "list_topics"))
                .thenAssertResults();
    }

    @Test
    void whenListTools_givenCollectionDescription_thenSearchToolCarriesIt() {
        TestClients.connect()
                .when()
                .toolsList(page -> assertThat(page.findByName("search").description())
                        .isEqualTo("Searches the test Collection."))
                .thenAssertResults();
    }

    @Test
    void whenPostAdminReIndex_givenNoToken_thenRejectAsUnauthorized() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(adminReIndex).POST(HttpRequest.BodyPublishers.noBody()).build();

        assertThat(http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
    }

    @Test
    void whenListTools_givenLowerCaseBearerScheme_thenAccept() {
        McpAssured.newStreamableClient()
                .setAdditionalHeaders(message -> MultiMap.caseInsensitiveMultiMap()
                        .add("Authorization", "bearer " + TestClients.FIRST_TOKEN))
                .build()
                .connect()
                .when()
                .toolsList(page -> assertThat(page.findByName("search")).isNotNull())
                .thenAssertResults();
    }

    @Test
    void whenGetReadiness_givenNoToken_thenAnswer() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(readiness).build();

        assertThat(http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
    }
}
