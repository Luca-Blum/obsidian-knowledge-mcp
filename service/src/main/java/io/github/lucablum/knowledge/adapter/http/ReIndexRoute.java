package io.github.lucablum.knowledge.adapter.http;

import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.indexing.ReIndexCollection;
import io.github.lucablum.knowledge.application.indexing.Reconciled;
import io.github.lucablum.knowledge.application.indexing.ReconciliationProgress;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

/**
 * {@code POST /admin/re-index} forces a full Re-Index, for when the index went wrong without the configuration
 * changing. It answers at once and runs the Re-Index in the background, as it takes hours; health reports its progress.
 */
@ApplicationScoped
@PrimaryAdapter
public class ReIndexRoute {

    private static final Logger LOG = Logger.getLogger(ReIndexRoute.class);

    private final ReIndexCollection reIndex;
    private final CollectionConfig collection;

    public ReIndexRoute(ReIndexCollection reIndex, CollectionConfig collection) {
        this.reIndex = reIndex;
        this.collection = collection;
    }

    void register(@Observes Router router) {
        router.post("/admin/re-index").handler(this::reIndex);
    }

    private void reIndex(RoutingContext request) {
        if (reIndex.running()) {
            String progress = reIndex.progress().map(ReconciliationProgress::describe).orElse("");
            request.response().setStatusCode(409).end("A Re-Index is already running: " + progress + " done.\n");
            return;
        }
        Thread.ofVirtual().name("re-index-" + collection.name()).start(this::reIndexInBackground);
        request.response().setStatusCode(202).end("Re-Index started; /q/health/ready reports its progress.\n");
    }

    /** Nothing waits for the thread, so its outcome is only logged. */
    private void reIndexInBackground() {
        try {
            Reconciled reconciled = reIndex.reIndex();
            LOG.infof("Re-Indexed Collection %s: %s", collection.name(), reconciled.summary());
        } catch (RuntimeException e) {
            LOG.errorf(e, "Re-Index of Collection %s failed", collection.name());
        }
    }
}
