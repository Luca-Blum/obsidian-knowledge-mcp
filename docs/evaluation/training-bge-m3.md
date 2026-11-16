# Evaluation of Collection training

Embedding model `bge-m3`, 76 Notes indexed, measured on 2026-11-15.

| Metric | Value |
| --- | --- |
| Hit@5 | 0.91 |
| Recall@5 | 0.75 |
| MRR | 0.81 |
| Questions | 45 |
| Minimum Similarity | 0.620 |
| Negative Questions passed | 16 of 16 |
| Highest similarity of a Negative Question | – |
| Negative Questions with full-text hits (information only) | 9 of 16 |
| Fusion | k 60, vector weight 1.00, full-text weight 1.00 |

## Per category

| Category | Questions | Hit@5 | Recall@5 | MRR |
| --- | --- | --- | --- | --- |
| definition | 10 | 1.00 | 0.95 | 0.93 |
| paraphrase | 7 | 0.71 | 0.64 | 0.71 |
| scenario | 7 | 0.71 | 0.55 | 0.46 |
| comparison | 6 | 1.00 | 0.74 | 0.92 |
| multi-note | 7 | 1.00 | 0.70 | 1.00 |
| cross-language | 8 | 1.00 | 0.83 | 0.79 |

## Negative Questions per kind

| Kind | Passed | Highest similarity |
| --- | --- | --- |
| other-collection | 10 of 10 | – |
| near-miss | 6 of 6 | – |

## Calibration

The baseline of the model comparison (#54) on the extended Evaluation Set. The Minimum Similarity is set just above
the highest similarity any Negative Question reaches at a Minimum Similarity of 0, on the same index and settings:

| Minimum Similarity | Hit@5 | Recall@5 | MRR | Highest similarity, other-collection | Highest similarity, near-miss |
| --- | --- | --- | --- | --- | --- |
| 0 | 0.93 | 0.76 | 0.83 | 0.576 | 0.613 |
| 0.62 (calibrated) | 0.91 | 0.75 | 0.81 | – | – |

The near-miss questions come closer than the engineering questions: a question about the golf swing reaches 0.613
against a passage of Racing Weight. The 0.60 calibrated in
[`training-minimum-similarity.md`](training-minimum-similarity.md) only kept the engineering questions out. Raising
it to 0.62 costs one paraphrase question.
