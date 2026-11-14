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
class RelatedToolTest {

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
    void whenCallRelatedTool_givenLinksOfEveryCategory_thenReturnFiveCategoriesWithAnchorsOnlyWhereWritten() {
        vault.write("Running/Plan.md", """
                ---
                source: "[[_sources/plan.pdf]]"
                ---
                [[Seiler#Polarized]] ![[Zones]] [[Missing]] [[Intervals]]""");
        vault.write("Running/Seiler.md", "Polarized training");
        vault.write("Running/Zones.md", "[[Plan]]");
        vault.write("Running/Intervals.md", "Running intervals");
        vault.write("Cycling/Intervals.md", "Cycling intervals");
        indexVault.run();

        client.when()
                .toolsCall("related", Map.of("path", "Running/Plan.md"), response -> {
                    assertThat(response.isError()).isFalse();
                    JsonObject related = json(response.firstContent());
                    assertThat(related.getJsonArray("links")).containsExactly(
                            new JsonObject().put("target", "Seiler").put("kind", "note").put("anchor", "Polarized")
                                    .put("notePath", "Running/Seiler.md"),
                            new JsonObject().put("target", "Zones").put("kind", "embed")
                                    .put("notePath", "Running/Zones.md"));
                    assertThat(related.getJsonArray("backlinks")).containsExactly(
                            new JsonObject().put("notePath", "Running/Zones.md").put("target", "Plan")
                                    .put("kind", "note"));
                    assertThat(related.getJsonArray("files")).containsExactly(
                            new JsonObject().put("target", "_sources/plan.pdf").put("kind", "file"));
                    assertThat(related.getJsonArray("unresolved")).containsExactly(
                            new JsonObject().put("target", "Missing").put("kind", "note"));
                    assertThat(related.getJsonArray("ambiguous")).containsExactly(
                            new JsonObject().put("target", "Intervals").put("kind", "note").put("candidates",
                                    new JsonArray().add("Cycling/Intervals.md").add("Running/Intervals.md")));
                })
                .thenAssertResults();
    }

    @Test
    void whenCallRelatedTool_givenPathThatIsNotIndexed_thenReturnNotFoundError() {
        vault.write("_eval/Questions.md", "[[Seiler]]");
        indexVault.run();

        client.when()
                .toolsCall("related", Map.of("path", "_eval/Questions.md"), response -> {
                    assertThat(response.isError()).isTrue();
                    assertThat(((TextContent) response.firstContent()).text()).contains("not found");
                })
                .thenAssertResults();
    }

    private static JsonObject json(Object content) {
        return new JsonObject(((TextContent) content).text());
    }
}
