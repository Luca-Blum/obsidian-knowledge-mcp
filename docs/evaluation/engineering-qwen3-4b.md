# Evaluation of Collection engineering

Embedding model `qwen3-embedding:4b`, 54 Notes indexed, measured on 2026-11-15.

| Metric | Value |
| --- | --- |
| Hit@5 | 0.98 |
| Recall@5 | 0.95 |
| MRR | 0.88 |
| Questions | 41 |
| Minimum Similarity | 0.550 |
| Negative Questions passed | 16 of 16 |
| Highest similarity of a Negative Question | – |
| Negative Questions with full-text hits (information only) | 5 of 16 |
| Fusion | k 60, vector weight 1.00, full-text weight 1.00 |

## Per category

| Category | Questions | Hit@5 | Recall@5 | MRR |
| --- | --- | --- | --- | --- |
| definition | 10 | 1.00 | 1.00 | 0.88 |
| paraphrase | 8 | 1.00 | 1.00 | 0.75 |
| scenario | 6 | 0.83 | 0.75 | 0.71 |
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
fusion as the `bge-m3` baseline, only the embedding model changes. Embedded by Ollama 0.40.16 natively on the Mac
(Metal), because on the CPU in Docker the index would have taken more than a day; the other models ran on Ollama 0.12.3
in Docker. The Minimum Similarity is set just above the highest similarity any Negative Question reaches at a Minimum
Similarity of 0, on the same index:

| Minimum Similarity | Hit@5 | Recall@5 | MRR | Highest similarity, other-collection | Highest similarity, near-miss |
| --- | --- | --- | --- | --- | --- |
| 0 | 1.00 | 0.97 | 0.90 | 0.457 | 0.546 |
| 0.55 (calibrated) | 0.98 | 0.95 | 0.88 | – | – |

The calibrated value costs one scenario question (Hit@5 1.00 at 0). Every Negative Question passes from 0.55 on, and
Hit@5 stays at 0.98 up to 0.60, so the value does not hinge on a single question (see [the
comparison](embedding-models.md#minimum-similarity)).