package io.github.lucablum.knowledge.adapter.health;

import io.github.lucablum.knowledge.application.indexing.ReIndexCollection;
import io.github.lucablum.knowledge.application.indexing.ReconciliationProgress;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

/**
 * Reports in the readiness check whether a Re-Index runs and how far it has come. It stays up meanwhile: reading and
 * browsing still work, and a Re-Index takes hours, far longer than a deployment waits for an instance to be ready.
 */
@ApplicationScoped
@Readiness
@PrimaryAdapter
public class ReIndexHealth implements HealthCheck {

    private final ReIndexCollection reIndex;

    public ReIndexHealth(ReIndexCollection reIndex) {
        this.reIndex = reIndex;
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder response = HealthCheckResponse.named("Re-Index").up();
        Optional<ReconciliationProgress> progress = reIndex.progress();
        if (progress.isEmpty()) {
            return response.withData("state", "idle").build();
        }
        return response.withData("state", "running").withData("progress", progress.get().describe()).build();
    }
}
