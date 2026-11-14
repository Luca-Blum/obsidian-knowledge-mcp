# Evaluation of Collection engineering

Embedding model `bge-m3`, 54 Notes indexed, measured on 2026-11-14.

| Metric | Value |
| --- | --- |
| Hit@5 | 1.00 |
| Recall@5 | 0.97 |
| MRR | 0.94 |
| Questions | 16 |
| Minimum Similarity | 0.560 |
| Negative Questions passed | 10 of 10 |
| Highest similarity of a Negative Question | – |
| Negative Questions with full-text hits (information only) | 4 of 10 |
| Fusion | k 60, vector weight 1.00, full-text weight 1.00 |

## Choosing the Minimum Similarity

All rows come from the same index and the same settings as
[the hybrid search evaluation with keywords](engineering-hybrid-search.md); only
`collection.search.minimum-similarity` changes. The Negative Questions are training questions, so this is the
engineering side of keeping the two Collections apart (#14).

| Minimum Similarity | Hit@5 | Recall@5 | MRR | Negative Questions passed |
| --- | --- | --- | --- | --- |
| 0 | 1.00 | 0.91 | 0.92 | 0 of 10 |
| 0.54 | 1.00 | 0.97 | 0.94 | 10 of 10 |
| 0.55 | 1.00 | 0.97 | 0.94 | 10 of 10 |
| 0.56 (chosen) | 1.00 | 0.97 | 0.94 | 10 of 10 |
| 0.58 | 1.00 | 0.97 | 0.94 | 10 of 10 |
| 0.60 | 1.00 | 0.97 | 0.89 | 10 of 10 |

The highest similarity any Negative Question reaches is 0.535, a marathon question against a Source Note on CPU
memory. From 0.54 on every Negative Question gets no vector hit, and up to 0.58 the real questions lose nothing: they
even gain Recall@5, because weak vector hits from unrelated books no longer take slots. At 0.60 the first expected
Notes start to drop in rank. 0.56 sits in the middle of that range, 0.025 above the closest Negative Question.

The similarities of real and Negative Questions overlap. Two questions find their expected Note only below 0.45
("Was ist Primitive Obsession?" at 0.437, "Was bedeutet Shameless Green?" at 0.366), so with 0.56 they are found only
by the full-text search on their `keywords`. A question the AI Agent asks without the exact term can therefore come
back empty where plain vector search would have found something.
