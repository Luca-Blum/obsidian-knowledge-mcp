package io.github.lucablum.knowledge.adapter.http;

import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.indexing.ReIndexCollection;
import io.github.lucablum.knowledge.application.indexing.ReIndexRunning;
import io.github.lucablum.knowledge.application.indexing.Reconciled;
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
        try {
            reIndex.reIndex(this::inBackground).whenComplete(this::log);
            request.response().setStatusCode(202).end("Re-Index started; /q/health/ready reports its progress.\n");
        } catch (ReIndexRunning e) {
            request.response().setStatusCode(409)
                    .end("A Re-Index is already running: " + e.progress().describe() + " done.\n");
        }
    }

    private void inBackground(Runnable reIndexing) {
        Thread.ofVirtual().name("re-index-" + collection.name()).start(reIndexing);
    }

    /** Nothing waits for the Re-Index, so its outcome is only logged. */
    private void log(Reconciled reconciled, Throwable failure) {
        if (failure != null) {
            LOG.errorf(failure, "Re-Index of Collection %s failed", collection.name());
        } else {
            LOG.infof("Re-Indexed Collection %s: %s", collection.name(), reconciled.summary());
        }
    }
}
