package io.github.lucablum.knowledge.domain.link;

import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Where a Note leads and what leads to it, with every Link resolved against the Notes indexed now: its Links to
 * Notes, its Backlinks, its Links to files, and its Unresolved and Ambiguous Links.
 */
@ValueObject
public record Related(
        List<ResolvedLink> links,
        List<Backlink> backlinks,
        List<Link> files,
        List<Link> unresolved,
        List<AmbiguousLink> ambiguous) {

    public Related {
        links = List.copyOf(links);
        backlinks = List.copyOf(backlinks);
        files = List.copyOf(files);
        unresolved = List.copyOf(unresolved);
        ambiguous = List.copyOf(ambiguous);
    }

    /**
     * Resolves the Links of {@code note} and finds its Backlinks. {@code linksOfEveryNote} holds every indexed Note,
     * also those without Links, because they are what a target is resolved against. Links to files are not resolved:
     * the index holds no files.
     */
    public static Related of(NotePath note, Map<NotePath, List<Link>> linksOfEveryNote) {
        LinkResolution resolution = new LinkResolution(linksOfEveryNote.keySet());
        List<Link> written = linksOfEveryNote.getOrDefault(note, List.of());
        Map<Link, List<NotePath>> matches = matchesOfLinksToNotes(written, resolution);
        return new Related(
                resolvedLinks(matches),
                backlinks(note, linksOfEveryNote, resolution),
                written.stream().filter(Link::pointsToFile).toList(),
                unresolvedLinks(matches),
                ambiguousLinks(matches));
    }

    /** The Notes each Link to a Note resolves to, in the order the Links are written. */
    private static Map<Link, List<NotePath>> matchesOfLinksToNotes(List<Link> links, LinkResolution resolution) {
        Map<Link, List<NotePath>> matches = new LinkedHashMap<>();
        links.stream()
                .filter(link -> !link.pointsToFile())
                .forEach(link -> matches.put(link, resolution.resolve(link.target())));
        return matches;
    }

    private static List<ResolvedLink> resolvedLinks(Map<Link, List<NotePath>> matches) {
        return matches.entrySet().stream()
                .filter(match -> match.getValue().size() == 1)
                .map(match -> new ResolvedLink(match.getKey(), match.getValue().getFirst()))
                .toList();
    }

    private static List<Link> unresolvedLinks(Map<Link, List<NotePath>> matches) {
        return matches.entrySet().stream()
                .filter(match -> match.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();
    }

    private static List<AmbiguousLink> ambiguousLinks(Map<Link, List<NotePath>> matches) {
        return matches.entrySet().stream()
                .filter(match -> match.getValue().size() > 1)
                .map(match -> new AmbiguousLink(match.getKey(), match.getValue()))
                .toList();
    }

    /** An Ambiguous Link is no Backlink to any of its candidates, and a Note's Links to itself are none either. */
    private static List<Backlink> backlinks(
            NotePath note, Map<NotePath, List<Link>> linksOfEveryNote, LinkResolution resolution) {
        List<Backlink> backlinks = new ArrayList<>();
        linksOfEveryNote.forEach((from, links) -> {
            if (!from.equals(note)) {
                links.stream()
                        .filter(link -> !link.pointsToFile() && resolution.resolve(link.target()).equals(List.of(note)))
                        .forEach(link -> backlinks.add(new Backlink(from, link)));
            }
        });
        return backlinks;
    }
}
