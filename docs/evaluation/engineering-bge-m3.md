# Evaluation of Collection engineering

Embedding model `bge-m3`, 54 Notes indexed, measured on 2026-11-15.

| Metric | Value |
| --- | --- |
| Hit@5 | 0.85 |
| Recall@5 | 0.80 |
| MRR | 0.79 |
| Questions | 41 |
| Minimum Similarity | 0.600 |
| Negative Questions passed | 16 of 16 |
| Highest similarity of a Negative Question | – |
| Negative Questions with full-text hits (information only) | 5 of 16 |
| Fusion | k 60, vector weight 1.00, full-text weight 1.00 |

## Per category

| Category | Questions | Hit@5 | Recall@5 | MRR |
| --- | --- | --- | --- | --- |
| definition | 10 | 1.00 | 0.95 | 0.88 |
| paraphrase | 8 | 0.50 | 0.50 | 0.38 |
| scenario | 6 | 0.67 | 0.67 | 0.67 |
| comparison | 6 | 1.00 | 1.00 | 1.00 |
| multi-note | 5 | 1.00 | 0.80 | 1.00 |
| cross-language | 6 | 1.00 | 0.92 | 0.92 |

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
| 0 | 0.90 | 0.83 | 0.82 | 0.535 | 0.593 |
| 0.60 (calibrated) | 0.85 | 0.80 | 0.79 | – | – |

The near-miss questions come closer than the training questions: a question about a multi-stage Dockerfile reaches
0.593 against a passage of the SRE book. The 0.56 calibrated in
[`engineering-minimum-similarity.md`](engineering-minimum-similarity.md) only kept the training questions out.
Raising it to 0.60 costs two paraphrase questions without `keywords`, whose best vector hits lie below 0.60
(paraphrase Hit@5 0.75 at 0, 0.50 at 0.60).
