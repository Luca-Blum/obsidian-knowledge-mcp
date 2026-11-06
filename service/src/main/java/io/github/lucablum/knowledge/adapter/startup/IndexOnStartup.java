package io.github.lucablum.knowledge.adapter.startup;

import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.IndexVault;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

/** Indexes the Collection's Vault in the background once the service has started. */
@ApplicationScoped
public class IndexOnStartup {

    private static final Logger LOG = Logger.getLogger(IndexOnStartup.class);

    private final IndexVault indexVault;
    private final CollectionConfig collection;

    public IndexOnStartup(IndexVault indexVault, CollectionConfig collection) {
        this.indexVault = indexVault;
        this.collection = collection;
    }

    void start(@Observes StartupEvent startup) {
        if (collection.indexOnStartup()) {
            Thread.ofVirtual().name("index-" + collection.name()).start(this::index);
        }
    }

    private void index() {
        LOG.infof("Indexing Collection %s from %s", collection.name(), collection.vaultPath());
        try {
            int notes = indexVault.run();
            LOG.infof("Indexed %d Notes of Collection %s", notes, collection.name());
        } catch (RuntimeException e) {
            LOG.errorf(e, "Indexing Collection %s failed", collection.name());
        }
    }
}
