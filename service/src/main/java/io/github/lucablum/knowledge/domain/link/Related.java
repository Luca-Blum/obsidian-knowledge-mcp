package io.github.lucablum.knowledge.domain.link;

import io.github.lucablum.knowledge.domain.note.NotePath;
import java.util.ArrayList;
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
        List<ResolvedLink> links = new ArrayList<>();
        List<Link> files = new ArrayList<>();
        List<Link> unresolved = new ArrayList<>();
        List<AmbiguousLink> ambiguous = new ArrayList<>();
        for (Link link : linksOfEveryNote.getOrDefault(note, List.of())) {
            List<NotePath> matches = link.pointsToFile() ? List.of() : resolution.resolve(link.target());
            if (link.pointsToFile()) {
                files.add(link);
            } else if (matches.isEmpty()) {
                unresolved.add(link);
            } else if (matches.size() == 1) {
                links.add(new ResolvedLink(link, matches.getFirst()));
            } else {
                ambiguous.add(new AmbiguousLink(link, matches));
            }
        }
        return new Related(links, backlinks(note, linksOfEveryNote, resolution), files, unresolved, ambiguous);
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
