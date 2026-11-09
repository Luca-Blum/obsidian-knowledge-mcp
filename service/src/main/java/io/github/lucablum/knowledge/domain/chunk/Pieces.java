package io.github.lucablum.knowledge.domain.chunk;

import io.github.lucablum.knowledge.domain.chunk.section.Block;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a section that is above the maximum at its blocks. A piece takes blocks until the next one would not fit;
 * the piece after it starts with the last words of its last block.
 */
final class Pieces {

    private Pieces() {
    }

    static List<String> of(List<Block> blocks, ChunkingRules rules) {
        List<Piece> pieces = new ArrayList<>(List.of(Piece.FIRST));
        for (Block block : blocks) {
            Piece current = pieces.getLast();
            if (current.hasNoBlocks() || current.plus(block).length() <= rules.maximumCharacters()) {
                pieces.set(pieces.size() - 1, current.plus(block));
            } else {
                pieces.add(current.next(rules.overlapCharacters()).plus(block));
            }
        }
        return pieces.stream().map(Piece::text).toList();
    }

    /** The blocks of one piece, after the words it repeats from the piece before. */
    private record Piece(String overlap, List<Block> blocks) {

        static final Piece FIRST = new Piece("", List.of());

        Piece plus(Block block) {
            List<Block> more = new ArrayList<>(blocks);
            more.add(block);
            return new Piece(overlap, more);
        }

        /** An empty piece that starts with the last words of this one. */
        Piece next(int overlapCharacters) {
            return new Piece(blocks.getLast().lastWords(overlapCharacters), List.of());
        }

        boolean hasNoBlocks() {
            return blocks.isEmpty();
        }

        int length() {
            return text().length();
        }

        String text() {
            return overlap.isEmpty() ? Block.text(blocks) : overlap + "\n\n" + Block.text(blocks);
        }
    }
}
