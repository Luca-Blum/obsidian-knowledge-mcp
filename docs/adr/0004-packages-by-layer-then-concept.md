# Packages are split by layer first, then by concept, and each use case gets its own view of the index

Status: Accepted · 2026-11-09

The hexagon's layers `domain`, `application` and `adapter` stay the top-level packages, because ADR 0003 and `ArchitectureTest` are built on them. Below them, `domain` and `application` are split by concept: `domain.note` (what a Note is), `domain.markdown` (the lines and headings of a Note's Markdown), `domain.chunk` (how a Note becomes Chunks), `domain.search`, `domain.catalog`, `domain.link` and `domain.reading` (what the AI Agent gets back when it reads a Note), and `application.indexing`, `application.search`, `application.catalog`, `application.link` and `application.reading`, with `Embedding` left in `application` because indexing and search both need it. `adapter` stays split by technology. The index is one Postgres schema, but the core sees it through secondary ports cut by use case: `IndexedNotes` for indexing, `IndexBuild` for the Re-Index, `ChunkVectors` and `ChunkFullText` for search, `Catalog` for browsing, `IndexedLinks` for following Links and `NoteTexts` for reading, all implemented by `PostgresIndex`. Search has two, because its vector and full-text rankings are separate lists that only the use case fuses.

## Considered Options

- **Concept first, layers inside** (`search/{domain,application,adapter}`). Rejected: `PostgresIndex`, `UseCases` and `CollectionConfig` serve every concept, so a shared package would be needed from the start, and the layer rules would have to match patterns across every concept.
- **One `Index` port.** Rejected: indexing, search and browsing each used a third of it, so every use case depended on methods it never calls, and a change for one of them touched the others.

## Consequences

- `ArchitectureTest` matches `..knowledge.application..` instead of the exact package, so the role rules cover the sub-packages. Two slice rules keep the concepts of `domain` and the use cases of `application` free of cycles, and `domain.note` may depend on no other domain concept.
- `domain.markdown` reads a Note's lines (`MarkdownLines`, `HeadingLine` and the rest) and tracks the headings above each of them (`OpenHeadings`, `HeadingPath`). Chunking, reading and Links all build on it, so a heading path a Search Hit shows is the one `read_note` finds, and none of them reaches into another's internals.
- `domain.chunk` is split by the steps a Note goes through: `domain.chunk.section` collects its lines into sections (`Sections`, `Section`, `Block`), and `domain.chunk` itself merges and splits sections into Chunks (`Splitter`, `ChunkingRules`, `Chunk`). `domain.chunk.section` builds on the public types of `domain.markdown`, `domain.chunk` on those of `domain.chunk.section`, and the slice rule treats both as the one concept `chunk`.
- Chunking and reading cut a Note at different headings on purpose: a Chunk's section ends at the next heading down to the configured level, a section `read_note` returns at the next heading of its own or a higher level, so it holds its subsections. `ChunkSectionTest` checks that reading a Chunk's heading path always returns what the Chunk holds.
- A new use case adds its own sub-package in `application` with its own secondary port, which `PostgresIndex` (or another adapter) implements.
