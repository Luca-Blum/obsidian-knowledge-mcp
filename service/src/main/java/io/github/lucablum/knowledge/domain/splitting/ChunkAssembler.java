package io.github.lucablum.knowledge.domain.splitting;

import io.github.lucablum.knowledge.domain.Chunk;
import io.github.lucablum.knowledge.domain.HeadingPath;
import io.github.lucablum.knowledge.domain.NotePath;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Collects the lines of a Note into Chunks, one per section, tracking the headings above the current one. */
final class ChunkAssembler {

    private final NotePath note;
    private final List<Chunk> chunks = new ArrayList<>();
    private final Deque<HeadingLine> headings = new ArrayDeque<>();
    private final StringBuilder section = new StringBuilder();

    ChunkAssembler(NotePath note) {
        this.note = note;
    }

    /** Ends the current section and opens one below {@code heading}. */
    void startSection(HeadingLine heading) {
        endSection();
        dropHeadingsFrom(heading.level());
        headings.addLast(heading);
    }

    void addLine(String line) {
        section.append(line).append('\n');
    }

    List<Chunk> finish() {
        endSection();
        return List.copyOf(chunks);
    }

    private void endSection() {
        String content = section.toString().strip();
        section.setLength(0);
        if (!content.isEmpty()) {
            chunks.add(new Chunk(note, chunks.size(), currentHeadingPath(), content));
        }
    }

    /** A heading closes every open heading of the same or a deeper level. */
    private void dropHeadingsFrom(int level) {
        while (!headings.isEmpty() && headings.peekLast().level() >= level) {
            headings.removeLast();
        }
    }

    private HeadingPath currentHeadingPath() {
        return new HeadingPath(headings.stream().map(HeadingLine::text).toList());
    }
}
