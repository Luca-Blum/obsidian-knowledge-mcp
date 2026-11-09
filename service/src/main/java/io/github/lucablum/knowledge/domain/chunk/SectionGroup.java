package io.github.lucablum.knowledge.domain.chunk;

import java.util.ArrayList;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Neighbouring sections that become one Chunk. Its heading path is the one all of them share, so reading that section
 * of the Note returns everything in the Chunk; a section deeper than that path shows its heading in the content.
 */
@ValueObject
record SectionGroup(List<Section> sections) {

    SectionGroup {
        sections = List.copyOf(sections);
    }

    static SectionGroup of(Section section) {
        return new SectionGroup(List.of(section));
    }

    SectionGroup plus(Section section) {
        List<Section> more = new ArrayList<>(sections);
        more.add(section);
        return new SectionGroup(more);
    }

    HeadingPath headings() {
        List<String> common = sections.getFirst().headings().segments();
        for (Section section : sections) {
            common = commonPrefix(common, section.headings().segments());
        }
        return new HeadingPath(common);
    }

    String content() {
        int shared = headings().segments().size();
        List<String> parts = new ArrayList<>();
        for (Section section : sections) {
            boolean deeper = section.headings().segments().size() > shared;
            parts.add(section.heading().filter(heading -> deeper)
                    .map(heading -> heading.line().strip() + "\n" + section.body())
                    .orElse(section.body()));
        }
        return String.join("\n\n", parts);
    }

    private static List<String> commonPrefix(List<String> a, List<String> b) {
        int length = 0;
        while (length < a.size() && length < b.size() && a.get(length).equals(b.get(length))) {
            length++;
        }
        return a.subList(0, length);
    }
}
