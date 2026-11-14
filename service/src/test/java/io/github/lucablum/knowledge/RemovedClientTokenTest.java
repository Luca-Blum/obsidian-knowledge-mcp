package io.github.lucablum.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Removing one Client's token locks out that Client and no other. */
@QuarkusTest
@TestProfile(RemovedClientTokenTest.WithoutSecondClient.class)
class RemovedClientTokenTest {

    @Test
    void whenConnect_givenTokenOfRemovedClient_thenRejectAsUnauthorized() {
        TestClients.expectRejected(TestClients.SECOND_TOKEN);
    }

    @Test
    void whenListTools_givenTokenOfRemainingClient_thenAccept() {
        TestClients.connectWith(TestClients.FIRST_TOKEN)
                .when()
                .toolsList(page -> assertThat(page.findByName("search")).isNotNull())
                .thenAssertResults();
    }

    /** The test profile with the second Client's token emptied, as removing it from {@code .env} does. */
    public static class WithoutSecondClient implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("collection.auth.tokens.second", "");
        }
    }
}
