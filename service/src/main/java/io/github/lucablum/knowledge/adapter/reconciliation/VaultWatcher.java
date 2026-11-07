package io.github.lucablum.knowledge.adapter.reconciliation;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import io.github.lucablum.knowledge.domain.VaultRules;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import org.jboss.logging.Logger;

/**
 * Reports changes in the Vault's folders through the file system's {@link WatchService}. A {@code WatchService}
 * watches single folders, so every folder is registered, including the ones created later. Folders that hold no
 * Notes are not watched, and hidden files such as Syncthing's temporary {@code .syncthing.*.tmp} are ignored: the
 * rename to the final name reports the change.
 */
final class VaultWatcher implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(VaultWatcher.class);

    private final Path root;
    private final WatchService watchService;
    private final Runnable onChange;

    private VaultWatcher(Path root, WatchService watchService, Runnable onChange) {
        this.root = root;
        this.watchService = watchService;
        this.onChange = onChange;
    }

    static VaultWatcher start(Path root, Runnable onChange) throws IOException {
        VaultWatcher watcher = new VaultWatcher(root, FileSystems.getDefault().newWatchService(), onChange);
        watcher.register(root);
        Thread.ofVirtual().name("watch-" + root.getFileName()).start(watcher::watch);
        return watcher;
    }

    @Override
    public void close() {
        try {
            watchService.close();
        } catch (IOException e) {
            LOG.debugf(e, "Closing the watch service of %s failed", root);
        }
    }

    private void watch() {
        try {
            while (true) {
                WatchKey key = watchService.take();
                if (handle(key)) {
                    onChange.run();
                }
                key.reset();
            }
        } catch (ClosedWatchServiceException e) {
            // closed on shutdown
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Registers new folders and returns whether any event may concern a Note. */
    private boolean handle(WatchKey key) {
        boolean relevant = false;
        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == OVERFLOW) {
                relevant = true;
                continue;
            }
            Path name = (Path) event.context();
            if (name.toString().startsWith(".")) {
                continue;
            }
            relevant = true;
            Path changed = ((Path) key.watchable()).resolve(name);
            if (event.kind() == ENTRY_CREATE && Files.isDirectory(changed)) {
                registerQuietly(changed);
            }
        }
        return relevant;
    }

    private void registerQuietly(Path folder) {
        try {
            register(folder);
        } catch (IOException e) {
            LOG.warnf(e, "Cannot watch %s; its changes show up only with the next scheduled Reconciliation", folder);
        }
    }

    private void register(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(root) && VaultRules.skipsFolder(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
