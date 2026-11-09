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
     * folder, and a path only at a folder boundary. A target that is a Note's whole path means only that Note;
     * otherwise several matches are all returned, never guessed between.
     */
    public List<NotePath> resolve(String target) {
        String path = withoutExtension(lowerCase(new NotePath(target.strip()).value()));
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        String wholePath = path;
        String suffix = "/" + path;
        List<NotePath> candidates = notesByFileName.getOrDefault(path.substring(path.lastIndexOf('/') + 1), List.of());
        List<NotePath> exact = candidates.stream()
                .filter(note -> withoutExtension(lowerCase(note.value())).equals(wholePath))
                .toList();
        return !exact.isEmpty() ? exact : candidates.stream()
                .filter(note -> withoutExtension(lowerCase(note.value())).endsWith(suffix))
                .toList();
    }

    private static String withoutExtension(String path) {
        return path.endsWith(".md") ? path.substring(0, path.length() - ".md".length()) : path;
    }

    private static String lowerCase(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
