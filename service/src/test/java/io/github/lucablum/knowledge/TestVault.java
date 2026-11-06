package io.github.lucablum.knowledge;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** The Collection's Vault on disk, as a test arranges it. */
@ApplicationScoped
public class TestVault {

    @ConfigProperty(name = "collection.vault-path")
    Path root;

    public void write(String relativePath, String content) {
        try {
            Path file = root.resolve(relativePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void clear() {
        try {
            if (Files.exists(root)) {
                try (Stream<Path> paths = Files.walk(root)) {
                    paths.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(root)).forEach(TestVault::delete);
                }
            }
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
