# Evaluation of Collection training

Embedding model `bge-m3`, 76 Notes indexed, measured on 2026-11-15.

| Metric | Value |
| --- | --- |
| Hit@5 | 1.00 |
| Recall@5 | 0.89 |
| MRR | 0.88 |
| Questions | 24 |
| Minimum Similarity | 0.600 |
| Negative Questions passed | 10 of 10 |
| Highest similarity of a Negative Question | – |
| Negative Questions with full-text hits (information only) | 5 of 10 |
| Fusion | k 60, vector weight 1.00, full-text weight 1.00 |

## Choosing the Minimum Similarity

All rows come from the same index and the same settings; only `collection.search.minimum-similarity` changes. The
Negative Questions are engineering questions, so this is the training side of keeping the two Collections apart (#14);
[`engineering-minimum-similarity.md`](engineering-minimum-similarity.md) is the other side.

| Minimum Similarity | Hit@5 | Recall@5 | MRR | Negative Questions passed |
| --- | --- | --- | --- | --- |
| 0 | 1.00 | 0.89 | 0.88 | 0 of 10 |
| 0.58 | 1.00 | 0.89 | 0.88 | 10 of 10 |
| 0.60 (chosen) | 1.00 | 0.89 | 0.88 | 10 of 10 |
| 0.62 | 1.00 | 0.89 | 0.88 | 10 of 10 |
| 0.65 | 0.96 | 0.89 | 0.89 | 10 of 10 |

The highest similarity any Negative Question reaches is 0.576, a question about tidying code against a passage in a
bike fitting book. From 0.58 on every Negative Question gets no vector hit, and up to 0.62 the real questions lose
nothing. At 0.65 the first question drops out: "Warum steigt mein Puls bei einem langen, gleichmässigen Lauf an, obwohl
das Tempo gleich bleibt?" has no `keywords`, so it depends on the vector search alone, and its best hit is 0.649.
0.60 sits in the middle of that range, 0.024 above the closest Negative Question and 0.014 below the weakest best hit
of a real question (0.614).

Unlike engineering, the similarities of real and Negative Questions do not overlap here: every real question's best
vector hit lies above every Negative Question's.
