# Architecture Decision Records

Each record states one decision, why it was made and what it costs. A decision that is replaced gets a new record,
and the old one is marked superseded rather than edited. Terms are defined in [`GLOSSARY.md`](../../GLOSSARY.md).

| ADR | Decision | Date |
| --- | --- | --- |
| [0001](0001-links-resolved-at-query-time.md) | Links are stored as written and resolved at query time | 2026-11-06 |
| [0002](0002-minimum-similarity-before-fusion.md) | Minimum Similarity is applied before rank fusion, not to the fused score | 2026-11-06 |
| [0003](0003-ports-are-interfaces-and-roles-are-annotated.md) | Ports are interfaces, and every architectural role is marked with a jMolecules annotation | 2026-11-06 |
| [0004](0004-packages-by-layer-then-concept.md) | Packages are split by layer first, then by concept, and each use case gets its own view of the index | 2026-11-09 |
| [0005](0005-claude-ai-uses-a-static-token-not-oauth.md) | claude.ai reaches a Collection with its static Client token, not through OAuth | 2026-11-14 |
| [0006](0006-qwen3-embedding-0.6b-on-the-pi.md) | The Collections on the Pi switch to `qwen3-embedding:0.6b` | 2026-11-16 |
