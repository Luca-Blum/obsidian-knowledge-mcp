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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ReadNoteToolTest {

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
    void whenCallReadNoteTool_givenShortNote_thenReturnItsText() {
        vault.write("Running/Intervals.md", "## Warm-up\nFifteen minutes easy.\n");
        indexVault.run();

        client.when()
                .toolsCall("read_note", Map.of("path", "Running/Intervals.md"), response -> {
                    assertThat(response.isError()).isFalse();
                    assertThat(json(response.firstContent())).isEqualTo(new JsonObject()
                            .put("notePath", "Running/Intervals.md")
                            .put("text", "## Warm-up\nFifteen minutes easy.\n"));
                })
                .thenAssertResults();
    }

    @Test
    void whenCallReadNoteTool_givenLongNote_thenReturnOutlineAndBeginning() {
        vault.write("Running/Marathon.md", "## Base\n%s\n## Taper\nThree weeks.\n".formatted("Easy miles. ".repeat(30)));
        indexVault.run();

        client.when()
                .toolsCall("read_note", Map.of("path", "Running/Marathon.md"), response -> {
                    assertThat(response.isError()).isFalse();
                    assertThat(json(response.firstContent())).isEqualTo(new JsonObject()
                            .put("notePath", "Running/Marathon.md")
                            .put("outline", new JsonArray().add(new JsonArray().add("Base"))
                                    .add(new JsonArray().add("Taper")))
                            .put("beginning", "## Base"));
                })
                .thenAssertResults();
    }

    @Test
    void whenCallReadNoteTool_givenHeadingPath_thenReturnMatchingSections() {
        vault.write("Running/Week.md", "## Tuesday\n### Notes\nIntervals.\n### Notes\nFelt good.\n## Wednesday\nRest.\n");
        indexVault.run();

        client.when()
                .toolsCall("read_note", Map.of("path", "Running/Week.md", "heading", List.of("Tuesday", "Notes")),
                        response -> {
                            assertThat(response.isError()).isFalse();
                            assertThat(json(response.firstContent())).isEqualTo(new JsonObject()
                                    .put("notePath", "Running/Week.md")
                                    .put("heading", new JsonArray().add("Tuesday").add("Notes"))
                                    .put("sections", new JsonArray()
                                            .add("### Notes\nIntervals.").add("### Notes\nFelt good.")));
                        })
                .thenAssertResults();
    }

    @Test
    void whenCallReadNoteTool_givenEmptyHeadingPathOfLongNote_thenReturnWholeBodyAsOneSection() {
        String body = "Short and hard.\n" + "## Set %d\nFour minutes.\n".repeat(20);
        vault.write("Running/Intervals.md", "---\ntitle: Intervals\n---\n" + body);
        indexVault.run();

        client.when()
                .toolsCall("read_note", Map.of("path", "Running/Intervals.md", "heading", List.of()), response ->
                        assertThat(json(response.firstContent()).getJsonArray("sections"))
                                .containsExactly(body.strip()))
                .thenAssertResults();
    }

    @Test
    void whenCallReadNoteTool_givenHeadingPathMatchingNoSection_thenReturnError() {
        vault.write("Running/Week.md", "## Tuesday\nIntervals.\n");
        indexVault.run();

        client.when()
                .toolsCall("read_note", Map.of("path", "Running/Week.md", "heading", List.of("Monday")),
                        response -> assertThat(response.isError()).isTrue())
                .thenAssertResults();
    }

    @Test
    void whenCallReadNoteTool_givenPathNotIndexed_thenReturnNotFoundError() {
        vault.write("_eval/questions.md", "Not a Note");
        indexVault.run();

        client.when()
                .toolsCall("read_note", Map.of("path", "_eval/questions.md"), response -> {
                    assertThat(response.isError()).isTrue();
                    assertThat(((TextContent) response.firstContent()).text()).startsWith("Note not found: _eval/questions.md.");
                })
                .toolsCall("read_note", Map.of("path", " "), response -> assertThat(response.isError()).isTrue())
                .thenAssertResults();
    }

    private static JsonObject json(Object content) {
        return new JsonObject(((TextContent) content).text());
    }
}
