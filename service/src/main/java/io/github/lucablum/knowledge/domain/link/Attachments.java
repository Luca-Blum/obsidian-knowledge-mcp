package io.github.lucablum.knowledge.domain.link;

import java.util.Locale;
import java.util.Set;

/**
 * The file formats Obsidian links to besides Notes. The index holds no other files, so a target is told apart by its
 * extension: {@code [[Dr. Seuss]]} still means a Note, {@code [[seiler.pdf]]} a file.
 */
final class Attachments {

    private static final Set<String> EXTENSIONS = Set.of(
            "avif", "bmp", "gif", "jpeg", "jpg", "png", "svg", "webp",
            "flac", "m4a", "mp3", "ogg", "wav",
            "3gp", "mkv", "mov", "mp4", "ogv", "webm",
            "pdf", "canvas", "base");

    private Attachments() {
    }

    static boolean isAttachment(String target) {
        int dot = target.lastIndexOf('.');
        return dot > target.lastIndexOf('/')
                && EXTENSIONS.contains(target.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
