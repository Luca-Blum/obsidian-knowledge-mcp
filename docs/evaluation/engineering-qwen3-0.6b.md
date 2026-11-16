# Evaluation of Collection engineering

Embedding model `qwen3-embedding:0.6b`, 54 Notes indexed, measured on 2026-11-16.

| Metric | Value |
| --- | --- |
| Hit@5 | 0.90 |
| Recall@5 | 0.84 |
| MRR | 0.83 |
| Questions | 41 |
| Minimum Similarity | 0.610 |
| Negative Questions passed | 16 of 16 |
| Highest similarity of a Negative Question | – |
| Negative Questions with full-text hits (information only) | 5 of 16 |
| Fusion | k 60, vector weight 1.00, full-text weight 1.00 |

## Per category

| Category | Questions | Hit@5 | Recall@5 | MRR |
| --- | --- | --- | --- | --- |
| definition | 10 | 1.00 | 0.95 | 0.90 |
| paraphrase | 8 | 0.75 | 0.75 | 0.63 |
| scenario | 6 | 0.67 | 0.67 | 0.67 |
| comparison | 6 | 1.00 | 0.92 | 0.92 |
| multi-note | 5 | 1.00 | 0.80 | 1.00 |
| cross-language | 6 | 1.00 | 0.92 | 0.92 |

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
| 0 | 0.98 | 0.96 | 0.89 | 0.450 | 0.601 |
| 0.61 (calibrated) | 0.90 | 0.84 | 0.83 | – | – |

Without a Minimum Similarity it finds an expected Note for all but one question, but one near-miss question reaches
0.601, above the best vector hits of three real questions. Keeping it out costs three questions, two scenario and one
paraphrase (Hit@5 0.98 at 0).