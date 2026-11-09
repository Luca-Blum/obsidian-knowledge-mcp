package io.github.lucablum.knowledge.domain.chunk;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reduces wikilinks to the text Obsidian shows for them, so link syntax does not distort a vector. */
final class Wikilinks {

    private static final Pattern WIKILINK = Pattern.compile("!?\\[\\[(?<target>[^\\]|]*)(?:\\|(?<alias>[^\\]]*))?]]");

    private Wikilinks() {
    }

    /** {@code [[Note|Alias]]} becomes "Alias", {@code [[Note#Heading]]} "Note > Heading"; embeds count as links. */
    static String toDisplayText(String text) {
        return WIKILINK.matcher(text).replaceAll(link -> Matcher.quoteReplacement(displayText(link)));
    }

    private static String displayText(MatchResult link) {
        if (link.group("alias") != null) {
            return link.group("alias");
        }
        String target = link.group("target");
        return target.startsWith("#") ? target.substring(1) : target.replace("#", " > ");
    }
}
