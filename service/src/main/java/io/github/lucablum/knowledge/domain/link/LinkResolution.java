package io.github.lucablum.knowledge.domain.link;

import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.jmolecules.ddd.annotation.Service;

/** Resolves a Link's target against the Notes the index holds now, the way Obsidian does. */
@Service
public class LinkResolution {

    /** The Notes by their file name in lower case, so a target is compared only with Notes that can match it. */
    private final Map<String, List<NotePath>> notesByFileName;

    public LinkResolution(Collection<NotePath> notes) {
        this.notesByFileName = notes.stream().collect(Collectors.groupingBy(note -> lowerCase(note.fileName())));
    }

    /**
     * Every Note whose path ends with {@code target}, ignoring case and {@code .md}: a file name matches in any
     * folder, and a path only at a folder boundary. Several matches are all returned, never guessed between.
     */
    public List<NotePath> resolve(String target) {
        String path = withoutExtension(lowerCase(new NotePath(target.strip()).value()));
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        String suffix = "/" + path;
        return notesByFileName.getOrDefault(path.substring(path.lastIndexOf('/') + 1), List.of()).stream()
                .filter(note -> {
                    String notePath = withoutExtension(lowerCase(note.value()));
                    return notePath.equals(suffix.substring(1)) || notePath.endsWith(suffix);
                })
                .toList();
    }

    private static String withoutExtension(String path) {
        return path.endsWith(".md") ? path.substring(0, path.length() - ".md".length()) : path;
    }

    private static String lowerCase(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
