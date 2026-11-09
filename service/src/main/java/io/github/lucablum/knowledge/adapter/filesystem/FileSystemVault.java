package io.github.lucablum.knowledge.adapter.filesystem;

import io.github.lucablum.knowledge.adapter.config.CollectionConfig;
import io.github.lucablum.knowledge.application.indexing.Vault;
import io.github.lucablum.knowledge.application.indexing.VaultNote;
import io.github.lucablum.knowledge.domain.note.NotePath;
import io.github.lucablum.knowledge.domain.note.VaultRules;
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
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;

/** Reads the Vault from the local file system; never writes to it. */
@ApplicationScoped
@SecondaryAdapter
public class FileSystemVault implements Vault {

    private final Path root;

    public FileSystemVault(CollectionConfig collection) {
        this.root = collection.vaultPath().toAbsolutePath().normalize();
    }

    @Override
    public List<VaultNote> notes() {
        List<VaultNote> notes = new ArrayList<>();
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
                        notes.add(new VaultNote(new NotePath(root.relativize(file).toString()), () -> read(file)));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return notes;
    }

    /** Reads from the file itself: on disk its name may be NFD while the NotePath is NFC. */
    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
