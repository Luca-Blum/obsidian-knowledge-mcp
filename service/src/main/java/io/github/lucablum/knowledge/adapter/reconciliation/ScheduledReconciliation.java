package io.github.lucablum.knowledge.adapter.reconciliation;

import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.indexing.IndexVault;
import io.github.lucablum.knowledge.application.indexing.ReIndexCollection;
import io.github.lucablum.knowledge.application.indexing.Reconciled;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;

/**
 * Runs Reconciliation in the background: right after start-up, then at the configured interval, and a quiet period
 * after a file in the Vault changed. All runs share one thread, so they never overlap. Before the first one, a Re-Index
 * rebuilds an index whose fingerprint differs from the configuration, so no Reconciliation ever mixes two models.
 */
@ApplicationScoped
@PrimaryAdapter
public class ScheduledReconciliation {

    private static final Logger LOG = Logger.getLogger(ScheduledReconciliation.class);

    private final IndexVault indexVault;
    private final ReIndexCollection reIndex;
    private final CollectionConfig collection;
    private ScheduledExecutorService executor;
    private VaultWatcher watcher;
    private ScheduledFuture<?> afterChange;

    public ScheduledReconciliation(IndexVault indexVault, ReIndexCollection reIndex, CollectionConfig collection) {
        this.indexVault = indexVault;
        this.reIndex = reIndex;
        this.collection = collection;
    }

    void start(@Observes StartupEvent startup) {
        CollectionConfig.Reconciliation config = collection.reconciliation();
        if (!config.enabled()) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("reconcile-" + collection.name()).daemon().factory());
        executor.execute(this::reIndexIfOutdated);
        executor.scheduleWithFixedDelay(this::reconcile, 0, config.interval().toMillis(), TimeUnit.MILLISECONDS);
        watch();
    }

    void stop(@Observes ShutdownEvent shutdown) {
        if (watcher != null) {
            watcher.close();
        }
        if (executor != null) {
            executor.shutdownNow();
            awaitRunningReconciliation();
        }
    }

    /** An interrupted run rolls back its current Note; waiting keeps it from outliving the datasource. */
    private void awaitRunningReconciliation() {
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                LOG.warnf("Reconciliation of Collection %s did not stop within 10 seconds", collection.name());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Without the watcher, changes still arrive with the next scheduled run, only later. */
    private void watch() {
        try {
            watcher = VaultWatcher.start(collection.vaultPath(), this::reconcileAfterChange);
        } catch (IOException | RuntimeException e) {
            LOG.warnf(e, "Cannot watch %s; changes show up only with the next scheduled Reconciliation",
                    collection.vaultPath());
        }
    }

    /** Every further change postpones the run, so a burst of changes leads to one Reconciliation. */
    private synchronized void reconcileAfterChange() {
        if (executor.isShutdown()) {
            return;
        }
        if (afterChange != null) {
            afterChange.cancel(false);
        }
        long quietPeriod = collection.reconciliation().quietPeriod().toMillis();
        afterChange = executor.schedule(this::reconcile, quietPeriod, TimeUnit.MILLISECONDS);
    }

    /** A failure is only logged; the fingerprint stays unrecorded, so the next start-up tries again. */
    private void reIndexIfOutdated() {
        try {
            reIndex.reIndexIfOutdated().ifPresent(reconciled -> LOG.infof(
                    "Re-Indexed Collection %s: indexed %d, removed %d, failed %d Notes", collection.name(),
                    reconciled.indexed(), reconciled.removed(), reconciled.failed().size()));
        } catch (RuntimeException e) {
            LOG.errorf(e, "Re-Index of Collection %s failed", collection.name());
        }
    }

    /** A failure is only logged, because a scheduled task that throws is never run again. */
    private void reconcile() {
        try {
            Reconciled reconciled = indexVault.run();
            if (reconciled.indexed() > 0 || reconciled.removed() > 0 || !reconciled.failed().isEmpty()) {
                LOG.infof("Reconciled Collection %s: indexed %d, removed %d, unchanged %d, failed %d Notes",
                        collection.name(), reconciled.indexed(), reconciled.removed(), reconciled.unchanged(),
                        reconciled.failed().size());
            }
        } catch (RuntimeException e) {
            LOG.errorf(e, "Reconciliation of Collection %s failed", collection.name());
        }
    }
}
