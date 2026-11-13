package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lucablum.knowledge.application.indexing.IndexVault;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SearchToolTest {

    @Inject
    TestVault vault;

    @Inject
    IndexVault indexVault;

    McpStreamableTestClient client;

    @BeforeEach
    void connect() {
        vault.clear();
        client = McpAssured.newConnectedStreamableClient();
    }

    @Test
    void whenListTools_givenCollectionDescription_thenSearchToolCarriesIt() {
        client.when()
                .toolsList(page -> assertThat(page.findByName("search").description())
                        .isEqualTo("Searches the test Collection."))
                .thenAssertResults();
    }

    @Test
    void whenCallSearchTool_givenTopicAndK_thenReturnSearchHits() {
        vault.write("Running/Intervals.md", """
                ## Workouts
                Interval training improves speed
                """);
        vault.write("Cycling/Intervals.md", "Interval training on the bike");
        indexVault.run();

        client.when()
                .toolsCall("search", Map.of("query", "interval training", "topic", "Running", "k", 3), response -> {
                    assertThat(response.isError()).isFalse();
                    JsonArray hits = new JsonObject(((TextContent) response.firstContent()).text()).getJsonArray("hits");
                    assertThat(hits).hasSize(1);
                    JsonObject hit = hits.getJsonObject(0);
                    assertThat(hit.getString("content")).isEqualTo("Interval training improves speed");
                    assertThat(hit.getString("notePath")).isEqualTo("Running/Intervals.md");
                    assertThat(hit.getJsonArray("headingPath")).containsExactly("Workouts");
                    assertThat(hit.getString("noteKind")).isEqualTo("own");
                    assertThat(hit.getDouble("score")).isBetween(-1.0, 1.0);
                    assertThat(hit.getInteger("moreInNote")).isZero();
                })
                .thenAssertResults();
    }

    @Test
    void whenCallSearchTool_givenKeywords_thenFindTheChunkContainingThem() {
        vault.write("Software-Engineering/Index.md", "The class PostgresIndex implements ChunkVectors.");
        indexVault.run();

        client.when()
                .toolsCall("search", Map.of("query", "where are embeddings kept", "keywords", "PostgresIndex"),
                        response -> {
                            JsonArray hits = new JsonObject(((TextContent) response.firstContent()).text())
                                    .getJsonArray("hits");
                            assertThat(hits).hasSize(1);
                            assertThat(hits.getJsonObject(0).getString("notePath"))
                                    .isEqualTo("Software-Engineering/Index.md");
                        })
                .thenAssertResults();
    }

    @Test
    void whenListTools_givenSearchTool_thenKeywordsExplainHowTheyDifferFromTheQuery() {
        client.when()
                .toolsList(page -> assertThat(page.findByName("search").inputSchema().getJsonObject("properties")
                        .getJsonObject("keywords").getString("description"))
                        .contains("exact", "full-text", "query"))
                .thenAssertResults();
    }

    @Test
    void whenCallSearchTool_givenKNotAWholeNumber_thenReturnError() {
        client.when()
                .toolsCall("search", Map.of("query", "interval training", "k", "ten"), response -> assertThat(response.isError()).isTrue())
                .thenAssertResults();
    }
}
