package io.github.lucablum.knowledge.domain.link;

import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
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
        String path = comparable(new NotePath(target.strip()).value());
        List<NotePath> candidates = notesByFileName.getOrDefault(fileName(path), List.of());
        List<NotePath> wholePath = matching(candidates, note -> note.equals(path));
        return !wholePath.isEmpty() ? wholePath : matching(candidates, note -> note.endsWith("/" + path));
    }

    private static List<NotePath> matching(List<NotePath> candidates, Predicate<String> path) {
        return candidates.stream().filter(note -> path.test(comparable(note.value()))).toList();
    }

    /** A path in lower case, without {@code .md} and leading slashes, as targets and Notes are compared. */
    private static String comparable(String path) {
        String lowerCase = lowerCase(path).replaceFirst("^/+", "");
        return lowerCase.endsWith(".md") ? lowerCase.substring(0, lowerCase.length() - ".md".length()) : lowerCase;
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String lowerCase(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
