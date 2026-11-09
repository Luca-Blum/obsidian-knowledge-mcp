# Evaluation of Collection engineering

Embedding model `bge-m3`, 54 Notes indexed, measured on 2026-11-09.

| Metric | Value |
| --- | --- |
| Hit@5 | 0.81 |
| Recall@5 | 0.72 |
| MRR | 0.71 |
| Questions | 16 |
| Minimum Similarity | 0.000 |
| Negative Questions passed | 0 of 10 |
| Highest similarity of a Negative Question | 0.535 |
| Negative Questions with full-text hits (information only) | 0 of 10 |
| Fusion | k 60, vector weight 1.00, full-text weight 1.00 |

## Against the baseline

All rows come from the same index, built for this run, with a Minimum Similarity of 0, like
[the baseline](engineering-baseline.md). Only `collection.search.fusion.full-text-weight` changes. The Evaluation Set's
questions carry no `keywords`, so the full-text search gets the whole question, and this measures only that fallback.

| Full-text weight | Hit@5 | Recall@5 | MRR |
| --- | --- | --- | --- |
| Baseline (2026-11-09, own index) | 0.88 | 0.75 | 0.80 |
| 0 (plain vector search) | 0.88 | 0.75 | 0.76 |
| 0.25 | 0.88 | 0.75 | 0.72 |
| 0.5 | 0.88 | 0.75 | 0.72 |
| 1.0 (default) | 0.81 | 0.72 | 0.71 |

Even with every term required, many questions match chunks of long book Source Notes, and at equal weight these push
expected Notes down or out of the top 5. No Negative Question got a full-text hit.
