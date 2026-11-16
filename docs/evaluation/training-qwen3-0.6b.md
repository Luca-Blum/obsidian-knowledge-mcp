# Evaluation of Collection training

Embedding model `qwen3-embedding:0.6b`, 76 Notes indexed, measured on 2026-11-16.

| Metric | Value |
| --- | --- |
| Hit@5 | 0.96 |
| Recall@5 | 0.81 |
| MRR | 0.86 |
| Questions | 45 |
| Minimum Similarity | 0.530 |
| Negative Questions passed | 16 of 16 |
| Highest similarity of a Negative Question | – |
| Negative Questions with full-text hits (information only) | 9 of 16 |
| Fusion | k 60, vector weight 1.00, full-text weight 1.00 |

## Per category

| Category | Questions | Hit@5 | Recall@5 | MRR |
| --- | --- | --- | --- | --- |
| definition | 10 | 1.00 | 0.95 | 0.95 |
| paraphrase | 7 | 1.00 | 0.86 | 0.75 |
| scenario | 7 | 0.86 | 0.62 | 0.69 |
| comparison | 6 | 1.00 | 0.79 | 1.00 |
| multi-note | 7 | 1.00 | 0.82 | 0.93 |
| cross-language | 8 | 0.88 | 0.77 | 0.81 |

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
| 0 | 0.98 | 0.82 | 0.85 | 0.507 | 0.526 |
| 0.53 (calibrated) | 0.96 | 0.81 | 0.86 | – | – |

The calibrated value costs one cross-language question (Hit@5 0.98 at 0).
