package io.github.lucablum.knowledge.adapter.filesystem;

import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.VaultReader;
import io.github.lucablum.knowledge.domain.NotePath;
import io.github.lucablum.knowledge.domain.VaultRules;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reads the Vault from the local file system; never writes to it. */
@ApplicationScoped
public class FileSystemVaultReader implements VaultReader {

    private final Path root;

    /** The file behind each Note as last scanned; on disk its name may be NFD while the NotePath is NFC. */
    private final Map<NotePath, Path> files = new ConcurrentHashMap<>();

    public FileSystemVaultReader(CollectionConfig collection) {
        this.root = collection.vaultPath().toAbsolutePath().normalize();
    }

    @Override
    public List<NotePath> notes() {
        List<NotePath> notes = new ArrayList<>();
        files.clear();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return !dir.equals(root) && VaultRules.skipsFolder(dir.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && VaultRules.isNoteFile(file.getFileName().toString())) {
                        NotePath note = new NotePath(root.relativize(file).toString());
                        files.put(note, file);
                        notes.add(note);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return notes;
    }

    @Override
    public String read(NotePath note) {
        try {
            Path file = files.get(note);
            if (file == null) {
                throw new IllegalArgumentException("Not a scanned Note: " + note);
            }
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
