# Evaluation of Collection engineering

Embedding model `snowflake-arctic-embed2`, 54 Notes indexed, measured on 2026-11-16.

| Metric | Value |
| --- | --- |
| Hit@5 | 0.90 |
| Recall@5 | 0.86 |
| MRR | 0.87 |
| Questions | 41 |
| Minimum Similarity | 0.480 |
| Negative Questions passed | 16 of 16 |
| Highest similarity of a Negative Question | – |
| Negative Questions with full-text hits (information only) | 5 of 16 |
| Fusion | k 60, vector weight 1.00, full-text weight 1.00 |

## Per category

| Category | Questions | Hit@5 | Recall@5 | MRR |
| --- | --- | --- | --- | --- |
| definition | 10 | 1.00 | 0.95 | 0.95 |
| paraphrase | 8 | 0.63 | 0.63 | 0.50 |
| scenario | 6 | 0.83 | 0.75 | 0.83 |
| comparison | 6 | 1.00 | 1.00 | 1.00 |
| multi-note | 5 | 1.00 | 0.87 | 1.00 |
| cross-language | 6 | 1.00 | 1.00 | 1.00 |

## Negative Questions per kind

| Kind | Passed | Highest similarity |
| --- | --- | --- |
| other-collection | 10 of 10 | – |
| near-miss | 6 of 6 | – |

## Calibration

Part of the model comparison (#54), see [`embedding-models.md`](embedding-models.md). Same Evaluation Set, chunking and
fusion as the `bge-m3` baseline, only the embedding model changes. Embedded by Ollama 0.12.3 in Docker, like the
`bge-m3` baseline. The Minimum Similarity is set just above the highest similarity any Negative Question reaches at a
Minimum Similarity of 0, on the same index:

| Minimum Similarity | Hit@5 | Recall@5 | MRR | Highest similarity, other-collection | Highest similarity, near-miss |
| --- | --- | --- | --- | --- | --- |
| 0 | 0.95 | 0.89 | 0.86 | 0.408 | 0.479 |
| 0.48 (calibrated) | 0.90 | 0.86 | 0.87 | – | – |

The calibrated value costs one paraphrase and one scenario question (Hit@5 0.95 at 0). Its similarities are lower across
the board, for the expected Notes too, so Hit@5 starts falling early: from 0.54 on it loses more questions than any
other model (see [the comparison](embedding-models.md#minimum-similarity)).