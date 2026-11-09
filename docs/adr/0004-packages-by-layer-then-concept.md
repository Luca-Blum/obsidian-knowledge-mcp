# Packages are split by layer first, then by concept, and each use case gets its own view of the index

The hexagon's layers `domain`, `application` and `adapter` stay the top-level packages, because ADR 0003 and `ArchitectureTest` are built on them. Below them, `domain` and `application` are split by concept: `domain.note` (what a Note is), `domain.chunk` (how a Note becomes Chunks), `domain.search` and `domain.catalog`, and `application.indexing`, `application.search` and `application.catalog`, with `Embedding` left in `application` because indexing and search both need it. `adapter` stays split by technology. The index is one Postgres schema, but the core sees it through one secondary port per use case: `IndexedNotes` for indexing, `ChunkVectors` for search and `Catalog` for browsing, all three implemented by `PostgresIndex`.

## Considered Options

- **Concept first, layers inside** (`search/{domain,application,adapter}`). Rejected: `PostgresIndex`, `UseCases` and `CollectionConfig` serve every concept, so a shared package would be needed from the start, and the layer rules would have to match patterns across every concept.
- **One `Index` port.** Rejected: indexing, search and browsing each used a third of it, so every use case depended on methods it never calls, and a change for one of them touched the others.

## Consequences

- `ArchitectureTest` matches `..knowledge.application..` instead of the exact package, so the role rules cover the sub-packages. Two slice rules keep the concepts of `domain` and the use cases of `application` free of cycles, and `domain.note` may depend on no other domain concept.
- `domain.chunk` is split by the steps a Note goes through: `domain.chunk.markdown` reads its lines (`MarkdownLines`, `HeadingLine` and the rest), `domain.chunk.section` collects them into sections (`Sections`, `Section`, `Block`, `HeadingPath`), and `domain.chunk` itself merges and splits sections into Chunks (`Splitter`, `ChunkingRules`, `Chunk`). Each step sees only the public types of the one before it, and the slice rule still treats all three as the one concept `chunk`.
- A new use case adds its own sub-package in `application` with its own secondary port, which `PostgresIndex` (or another adapter) implements.
