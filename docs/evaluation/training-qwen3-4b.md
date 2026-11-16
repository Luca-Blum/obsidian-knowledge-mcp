# Evaluation of Collection training

Embedding model `qwen3-embedding:4b`, 76 Notes indexed, measured on 2026-11-15.

| Metric | Value |
| --- | --- |
| Hit@5 | 0.93 |
| Recall@5 | 0.79 |
| MRR | 0.86 |
| Questions | 45 |
| Minimum Similarity | 0.540 |
| Negative Questions passed | 16 of 16 |
| Highest similarity of a Negative Question | – |
| Negative Questions with full-text hits (information only) | 9 of 16 |
| Fusion | k 60, vector weight 1.00, full-text weight 1.00 |

## Per category

| Category | Questions | Hit@5 | Recall@5 | MRR |
| --- | --- | --- | --- | --- |
| definition | 10 | 1.00 | 1.00 | 1.00 |
| paraphrase | 7 | 0.86 | 0.71 | 0.76 |
| scenario | 7 | 0.86 | 0.55 | 0.69 |
| comparison | 6 | 1.00 | 0.88 | 0.92 |
| multi-note | 7 | 1.00 | 0.82 | 1.00 |
| cross-language | 8 | 0.88 | 0.71 | 0.73 |

## Negative Questions per kind

| Kind | Passed | Highest similarity |
| --- | --- | --- |
| other-collection | 10 of 10 | – |
| near-miss | 6 of 6 | – |

## Calibration

Part of the model comparison (#54), see [`embedding-models.md`](embedding-models.md). Same Evaluation Set, chunking and
fusion as the `bge-m3` baseline, only the embedding model changes. Embedded by Ollama 0.40.16 natively on the Mac
(Metal), because on the CPU in Docker the index would have taken more than a day; the other models ran on Ollama 0.12.3
in Docker. The Minimum Similarity is set just above the highest similarity any Negative Question reaches at a Minimum
Similarity of 0, on the same index:

| Minimum Similarity | Hit@5 | Recall@5 | MRR | Highest similarity, other-collection | Highest similarity, near-miss |
| --- | --- | --- | --- | --- | --- |
| 0 | 0.96 | 0.79 | 0.86 | 0.473 | 0.531 |
| 0.54 (calibrated) | 0.93 | 0.79 | 0.86 | – | – |

The calibrated value costs one cross-language question (Hit@5 0.96 at 0).
