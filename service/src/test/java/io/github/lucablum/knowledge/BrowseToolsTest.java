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
class BrowseToolsTest {

    @Inject
    TestVault vault;

    @Inject
    IndexVault indexVault;

    McpStreamableTestClient client;

    @BeforeEach
    void connect() {
        vault.clear();
        client = TestClients.connect();
    }

    @Test
    void whenCallListTopicsTool_givenNotesInTopics_thenReturnTopicsWithNoteCounts() {
        vault.write("Running/Intervals.md", "Interval training");
        vault.write("Running/Plans/Marathon.md", "Marathon plan");
        indexVault.run();

        client.when()
                .toolsCall("list_topics", Map.of(), response -> {
                    assertThat(response.isError()).isFalse();
                    JsonArray topics = json(response.firstContent()).getJsonArray("topics");
                    assertThat(topics).containsExactly(new JsonObject().put("topic", "Running").put("notes", 2));
                })
                .thenAssertResults();
    }

    @Test
    void whenCallListSourcesTool_givenSourceNotes_thenReturnTitleDateAndAuthorOnlyWhenKnown() {
        vault.write("Running/Source-Notes/Daniels.md", """
                ---
                type: source
                title: Daniels' Running Formula
                author: Jack Daniels
                imported: 2026-01-05
                published: 2021
                ---
                text""");
        vault.write("Running/Source-Notes/Seiler.md", "---\ntype: source\n---\ntext");
        indexVault.run();

        client.when()
                .toolsCall("list_sources", Map.of("topic", "Running"), response -> {
                    assertThat(response.isError()).isFalse();
                    JsonArray sources = json(response.firstContent()).getJsonArray("sources");
                    assertThat(sources).containsExactly(
                            new JsonObject()
                                    .put("notePath", "Running/Source-Notes/Daniels.md")
                                    .put("title", "Daniels' Running Formula")
                                    .put("date", "2021")
                                    .put("author", "Jack Daniels"),
                            new JsonObject()
                                    .put("notePath", "Running/Source-Notes/Seiler.md")
                                    .put("title", "Seiler"));
                })
                .thenAssertResults();
    }

    @Test
    void whenCallListSourcesTool_givenBlankTopic_thenReturnError() {
        client.when()
                .toolsCall("list_sources", Map.of("topic", " "), response -> assertThat(response.isError()).isTrue())
                .thenAssertResults();
    }

    private static JsonObject json(Object content) {
        return new JsonObject(((TextContent) content).text());
    }
}
