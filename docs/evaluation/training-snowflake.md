# Evaluation of Collection training

Embedding model `snowflake-arctic-embed2`, 76 Notes indexed, measured on 2026-11-16.

| Metric | Value |
| --- | --- |
| Hit@5 | 0.91 |
| Recall@5 | 0.79 |
| MRR | 0.85 |
| Questions | 45 |
| Minimum Similarity | 0.460 |
| Negative Questions passed | 16 of 16 |
| Highest similarity of a Negative Question | – |
| Negative Questions with full-text hits (information only) | 9 of 16 |
| Fusion | k 60, vector weight 1.00, full-text weight 1.00 |

## Per category

| Category | Questions | Hit@5 | Recall@5 | MRR |
| --- | --- | --- | --- | --- |
| definition | 10 | 1.00 | 1.00 | 0.93 |
| paraphrase | 7 | 0.86 | 0.71 | 0.86 |
| scenario | 7 | 0.71 | 0.55 | 0.57 |
| comparison | 6 | 1.00 | 0.74 | 0.92 |
| multi-note | 7 | 1.00 | 0.87 | 1.00 |
| cross-language | 8 | 0.88 | 0.77 | 0.79 |

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
| 0 | 0.91 | 0.79 | 0.85 | 0.401 | 0.458 |
| 0.46 (calibrated) | 0.91 | 0.79 | 0.85 | – | – |

The calibrated value costs nothing here: Hit@5, Recall@5 and MRR are the same as at 0. Its weak spot is cross-language,
the only model that misses a German question about an English Note even without a Minimum Similarity.