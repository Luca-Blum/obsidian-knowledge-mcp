package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.agroal.api.AgroalDataSource;
import io.github.lucablum.knowledge.application.indexing.IndexVault;
import io.github.lucablum.knowledge.application.indexing.Progress;
import io.github.lucablum.knowledge.application.indexing.ReIndexCollection;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Health reports a running Re-Index, and the admin endpoint forces one. */
@QuarkusTest
class ReIndexHttpTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @TestHTTPResource("/q/health/ready")
    URI readiness;

    @TestHTTPResource("/admin/re-index")
    URI adminReIndex;

    @Inject
    TestVault vault;

    @Inject
    IndexVault indexVault;

    @Inject
    ReIndexCollection reIndex;

    @Inject
    AgroalDataSource dataSource;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void emptyVault() {
        vault.clear();
    }

    @Test
    void whenGetReadiness_givenReIndexRunning_thenStayUpAndReportItsProgress() throws Exception {
        QuarkusMock.installMockForType(new RunningReIndex(new Progress(12, 340)), ReIndexCollection.class);

        HttpResponse<String> response = http.send(HttpRequest.newBuilder(readiness).build(), ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonObject check = reIndexCheck(response);
        assertThat(check.getString("status")).isEqualTo("UP");
        assertThat(check.getJsonObject("data").getString("state")).isEqualTo("running");
        assertThat(check.getJsonObject("data").getString("progress")).isEqualTo("12 of 340 Notes");
    }

    @Test
    void whenGetReadiness_givenNoReIndexRunning_thenReportItIdle() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(readiness).build(), ofString());

        assertThat(reIndexCheck(response).getJsonObject("data").getString("state")).isEqualTo("idle");
    }

    @Test
    void whenPostAdminReIndex_givenChunksOfAnOldModel_thenAcceptAndReIndexInTheBackground() throws Exception {
        vault.write("Running/Tempo.md", "Tempo runs at threshold pace");
        indexVault.run();
        markEveryChunkAsEmbeddedBy("old-model");

        HttpResponse<String> response = http.send(post(adminReIndex), ofString());

        assertThat(response.statusCode()).isEqualTo(202);
        awaitReIndexWithin(TIMEOUT);
        assertThat(chunksEmbeddedBy("old-model")).isZero();
        assertThat(chunksEmbeddedBy(FakeEmbedding.MODEL)).isOne();
    }

    @Test
    void whenPostAdminReIndex_givenReIndexRunning_thenRejectWithConflictNamingItsProgress() throws Exception {
        QuarkusMock.installMockForType(new RunningReIndex(new Progress(12, 340)), ReIndexCollection.class);

        HttpResponse<String> response = http.send(post(adminReIndex), ofString());

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("12 of 340 Notes");
    }

    private static JsonObject reIndexCheck(HttpResponse<String> response) {
        return new JsonObject(response.body()).getJsonArray("checks").stream()
                .map(JsonObject.class::cast)
                .filter(check -> check.getString("name").equals("Re-Index"))
                .findFirst()
                .orElseThrow();
    }

    private static HttpRequest post(URI uri) {
        return HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.noBody()).build();
    }

    private static HttpResponse.BodyHandler<String> ofString() {
        return HttpResponse.BodyHandlers.ofString();
    }

    /** Polls until a Chunk records the new model and no Re-Index runs, or {@code timeout} has passed. */
    private void awaitReIndexWithin(Duration timeout) throws SQLException, InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while ((chunksEmbeddedBy(FakeEmbedding.MODEL) == 0 || reIndex.progress().isPresent())
                && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
    }

    private void markEveryChunkAsEmbeddedBy(String model) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().executeUpdate(
                    "UPDATE test_collection.chunk SET embedding_model = '" + model + "'");
        }
    }

    private int chunksEmbeddedBy(String model) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                ResultSet rows = connection.createStatement().executeQuery(
                        "SELECT count(*) FROM test_collection.chunk WHERE embedding_model = '" + model + "'")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
