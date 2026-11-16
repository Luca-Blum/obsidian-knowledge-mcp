# Embedding models compared

Four embedding models on both extended Evaluation Sets (#54): `bge-m3`, the model the Collections have used so far,
`snowflake-arctic-embed2`, `qwen3-embedding:0.6b` and `qwen3-embedding:4b`. The decision is recorded in
[ADR 0006](../adr/0006-qwen3-embedding-0.6b-on-the-pi.md).

Everything but the embedding model is the same as in the [`bge-m3` baseline](engineering-bge-m3.md): Evaluation Set,
chunking rules, fusion (k 60, both weights 1.00), and `keywords` where a question has them. Each model gets its own
Minimum Similarity, calibrated just above the highest similarity any of its Negative Questions reaches, because every
model has its own similarity scale. The models ran on Ollama 0.12.3 in Docker, except `qwen3-embedding:4b`, which ran
on Ollama 0.40.16 natively on the Mac, because on the CPU in Docker its index would have taken more than a day.

A difference counts only if it is larger than one question; anything smaller is a tie.

## Results

Each model at its calibrated Minimum Similarity, where every Negative Question passes. Details per model:
[engineering](engineering-bge-m3.md) and [training](training-bge-m3.md) for `bge-m3`, and likewise
`<collection>-snowflake.md`, `<collection>-qwen3-0.6b.md` and `<collection>-qwen3-4b.md`.

### Engineering

| Model | Minimum Similarity | Hit@5 | Questions found | Recall@5 | MRR |
| --- | --- | --- | --- | --- | --- |
| `bge-m3` | 0.60 | 0.85 | 35 of 41 | 0.80 | 0.79 |
| `snowflake-arctic-embed2` | 0.48 | 0.90 | 37 of 41 | 0.86 | 0.87 |
| `qwen3-embedding:0.6b` | 0.61 | 0.90 | 37 of 41 | 0.84 | 0.83 |
| `qwen3-embedding:4b` | 0.55 | 0.98 | 40 of 41 | 0.95 | 0.88 |

Hit@5 per category:

| Category | Questions | `bge-m3` | `snowflake-arctic-embed2` | `qwen3-embedding:0.6b` | `qwen3-embedding:4b` |
| --- | --- | --- | --- | --- | --- |
| definition | 10 | 1.00 | 1.00 | 1.00 | 1.00 |
| paraphrase | 8 | 0.50 | 0.63 | 0.75 | 1.00 |
| scenario | 6 | 0.67 | 0.83 | 0.67 | 0.83 |
| comparison | 6 | 1.00 | 1.00 | 1.00 | 1.00 |
| multi-note | 5 | 1.00 | 1.00 | 1.00 | 1.00 |
| cross-language | 6 | 1.00 | 1.00 | 1.00 | 1.00 |


### Training

| Model | Minimum Similarity | Hit@5 | Questions found | Recall@5 | MRR |
| --- | --- | --- | --- | --- | --- |
| `bge-m3` | 0.62 | 0.91 | 41 of 45 | 0.75 | 0.81 |
| `snowflake-arctic-embed2` | 0.46 | 0.91 | 41 of 45 | 0.79 | 0.85 |
| `qwen3-embedding:0.6b` | 0.53 | 0.96 | 43 of 45 | 0.81 | 0.86 |
| `qwen3-embedding:4b` | 0.54 | 0.93 | 42 of 45 | 0.79 | 0.86 |

Hit@5 per category:

| Category | Questions | `bge-m3` | `snowflake-arctic-embed2` | `qwen3-embedding:0.6b` | `qwen3-embedding:4b` |
| --- | --- | --- | --- | --- | --- |
| definition | 10 | 1.00 | 1.00 | 1.00 | 1.00 |
| paraphrase | 7 | 0.71 | 0.86 | 1.00 | 0.86 |
| scenario | 7 | 0.71 | 0.71 | 0.86 | 0.86 |
| comparison | 6 | 1.00 | 1.00 | 1.00 | 1.00 |
| multi-note | 7 | 1.00 | 1.00 | 1.00 | 1.00 |
| cross-language | 8 | 1.00 | 0.88 | 0.88 | 0.88 |


### Verdict

- **Engineering:** `qwen3-embedding:4b` is ahead of every other model (40 of 41). `snowflake-arctic-embed2` and
  `qwen3-embedding:0.6b` tie (37), and both are ahead of `bge-m3` (35). The gap is in paraphrase and scenario
  questions: `bge-m3` finds 4 of 8 paraphrase questions, `qwen3-embedding:4b` all 8.
- **Training:** `qwen3-embedding:0.6b` (43 of 45) is ahead of `bge-m3` and `snowflake-arctic-embed2` (41 each), and
  ties with `qwen3-embedding:4b` (42), which in turn ties with `bge-m3`.
- `bge-m3` is ahead of no other model on either Collection. Definition, comparison and multi-note questions do not
  separate the models; all of them find every one.

## Minimum Similarity

The calibrated value is a single point: the lowest Minimum Similarity at which every Negative Question passes. To see
whether a model is ahead only at that point, every model was also run at every value from 0.35 to 0.65 in steps of
0.01, on the same index.

![Hit@5 and Negative Questions passed per Minimum Similarity, per model and Collection](../images/embedding-models-minimum-similarity.svg)

- `bge-m3` has no margin. On both Collections it loses real questions one step before its last Negative Question
  passes, at 0.59 on engineering and 0.61 on training: its near-miss questions reach the same similarity as some of
  the real ones. The Pi's current values, 0.56 for engineering and 0.60 for training, let 3 and 1 of the 16 Negative
  Questions through.
- `qwen3-embedding:4b` has the widest margin: on engineering every Negative Question passes from 0.55 on, and Hit@5
  stays at 0.98 up to 0.60.
- `qwen3-embedding:0.6b` has a margin on training: Hit@5 stays at 0.96 from 0.50 to 0.54. On engineering it has
  none: one near-miss question reaches 0.601, and keeping it out costs three questions.
- `snowflake-arctic-embed2` keeps its Negative Questions furthest away, but its similarities are lower overall, for the
  real questions too: just above its calibrated values it keeps losing them, from 0.50 on training and 0.54 on
  engineering.
- A higher Minimum Similarity can only lower Hit@5 and Recall@5, but MRR can rise slightly: the Minimum Similarity
  removes vector hits before the fusion, and a removed hit can let an expected Note move up a rank.

<details>
<summary>Data: Hit@5 · Negative Questions passed (of 16) per Minimum Similarity</summary>

| Minimum Similarity | Eng. `bge-m3` | Eng. `snowflake-arctic-embed2` | Eng. `qwen3-embedding:0.6b` | Eng. `qwen3-embedding:4b` | Tra. `bge-m3` | Tra. `snowflake-arctic-embed2` | Tra. `qwen3-embedding:0.6b` | Tra. `qwen3-embedding:4b` |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 0.35 | 0.90 · 0 | 0.95 · 8 | 0.98 · 3 | 1.00 · 2 | 0.93 · 0 | 0.91 · 6 | 0.98 · 1 | 0.96 · 1 |
| 0.36 | 0.90 · 0 | 0.95 · 8 | 0.98 · 3 | 1.00 · 2 | 0.93 · 0 | 0.91 · 6 | 0.98 · 1 | 0.96 · 3 |
| 0.37 | 0.90 · 0 | 0.95 · 10 | 0.98 · 3 | 1.00 · 4 | 0.93 · 0 | 0.91 · 7 | 0.98 · 1 | 0.96 · 3 |
| 0.38 | 0.90 · 0 | 0.95 · 11 | 0.98 · 3 | 1.00 · 4 | 0.93 · 0 | 0.91 · 10 | 0.98 · 1 | 0.96 · 5 |
| 0.39 | 0.90 · 0 | 0.95 · 11 | 0.98 · 3 | 1.00 · 4 | 0.93 · 0 | 0.91 · 11 | 0.98 · 1 | 0.96 · 6 |
| 0.40 | 0.90 · 0 | 0.95 · 11 | 0.98 · 5 | 1.00 · 5 | 0.93 · 0 | 0.91 · 11 | 0.98 · 2 | 0.96 · 6 |
| 0.41 | 0.90 · 0 | 0.95 · 12 | 0.98 · 6 | 1.00 · 7 | 0.93 · 1 | 0.91 · 13 | 0.98 · 2 | 0.96 · 6 |
| 0.42 | 0.90 · 0 | 0.95 · 14 | 0.98 · 6 | 1.00 · 8 | 0.93 · 1 | 0.91 · 13 | 0.98 · 2 | 0.96 · 6 |
| 0.43 | 0.90 · 0 | 0.95 · 15 | 0.98 · 7 | 1.00 · 8 | 0.93 · 2 | 0.91 · 14 | 0.98 · 2 | 0.96 · 6 |
| 0.44 | 0.90 · 0 | 0.95 · 15 | 0.98 · 8 | 1.00 · 10 | 0.93 · 3 | 0.91 · 14 | 0.98 · 5 | 0.96 · 7 |
| 0.45 | 0.90 · 0 | 0.95 · 15 | 0.98 · 9 | 1.00 · 10 | 0.93 · 3 | 0.91 · 14 | 0.98 · 7 | 0.96 · 8 |
| 0.46 | 0.90 · 1 | 0.95 · 15 | 0.98 · 10 | 1.00 · 12 | 0.93 · 3 | 0.91 · 16 | 0.98 · 9 | 0.96 · 9 |
| 0.47 | 0.90 · 1 | 0.95 · 15 | 0.98 · 10 | 1.00 · 13 | 0.93 · 3 | 0.91 · 16 | 0.98 · 9 | 0.96 · 10 |
| 0.48 | 0.90 · 3 | 0.90 · 16 | 0.98 · 10 | 1.00 · 14 | 0.93 · 5 | 0.91 · 16 | 0.98 · 9 | 0.96 · 14 |
| 0.49 | 0.90 · 4 | 0.90 · 16 | 0.98 · 11 | 1.00 · 14 | 0.93 · 6 | 0.91 · 16 | 0.98 · 12 | 0.96 · 15 |
| 0.50 | 0.90 · 5 | 0.90 · 16 | 0.98 · 13 | 1.00 · 15 | 0.93 · 6 | 0.89 · 16 | 0.96 · 13 | 0.96 · 15 |
| 0.51 | 0.90 · 7 | 0.90 · 16 | 0.95 · 13 | 1.00 · 15 | 0.93 · 7 | 0.89 · 16 | 0.96 · 14 | 0.96 · 15 |
| 0.52 | 0.90 · 10 | 0.90 · 16 | 0.95 · 13 | 0.98 · 15 | 0.93 · 8 | 0.89 · 16 | 0.96 · 15 | 0.96 · 15 |
| 0.53 | 0.90 · 11 | 0.90 · 16 | 0.95 · 13 | 0.98 · 15 | 0.93 · 9 | 0.89 · 16 | 0.96 · 16 | 0.96 · 15 |
| 0.54 | 0.90 · 12 | 0.85 · 16 | 0.95 · 13 | 0.98 · 15 | 0.93 · 10 | 0.87 · 16 | 0.96 · 16 | 0.93 · 16 |
| 0.55 | 0.90 · 12 | 0.83 · 16 | 0.95 · 13 | 0.98 · 16 | 0.93 · 13 | 0.87 · 16 | 0.93 · 16 | 0.93 · 16 |
| 0.56 | 0.90 · 13 | 0.83 · 16 | 0.95 · 14 | 0.98 · 16 | 0.93 · 13 | 0.87 · 16 | 0.93 · 16 | 0.91 · 16 |
| 0.57 | 0.90 · 14 | 0.83 · 16 | 0.95 · 15 | 0.98 · 16 | 0.93 · 13 | 0.87 · 16 | 0.93 · 16 | 0.91 · 16 |
| 0.58 | 0.90 · 15 | 0.78 · 16 | 0.95 · 15 | 0.98 · 16 | 0.93 · 15 | 0.84 · 16 | 0.93 · 16 | 0.91 · 16 |
| 0.59 | 0.85 · 15 | 0.78 · 16 | 0.93 · 15 | 0.98 · 16 | 0.93 · 15 | 0.84 · 16 | 0.93 · 16 | 0.91 · 16 |
| 0.60 | 0.85 · 16 | 0.76 · 16 | 0.90 · 15 | 0.98 · 16 | 0.93 · 15 | 0.84 · 16 | 0.93 · 16 | 0.91 · 16 |
| 0.61 | 0.85 · 16 | 0.76 · 16 | 0.90 · 16 | 0.95 · 16 | 0.91 · 15 | 0.84 · 16 | 0.93 · 16 | 0.91 · 16 |
| 0.62 | 0.83 · 16 | 0.76 · 16 | 0.88 · 16 | 0.95 · 16 | 0.91 · 16 | 0.84 · 16 | 0.93 · 16 | 0.89 · 16 |
| 0.63 | 0.83 · 16 | 0.76 · 16 | 0.85 · 16 | 0.93 · 16 | 0.91 · 16 | 0.82 · 16 | 0.91 · 16 | 0.89 · 16 |
| 0.64 | 0.83 · 16 | 0.73 · 16 | 0.80 · 16 | 0.93 · 16 | 0.91 · 16 | 0.82 · 16 | 0.89 · 16 | 0.84 · 16 |
| 0.65 | 0.83 · 16 | 0.73 · 16 | 0.80 · 16 | 0.90 · 16 | 0.89 · 16 | 0.82 · 16 | 0.89 · 16 | 0.82 · 16 |

</details>

## On the Pi

The Pi 5 (8 GB, CPU only, Ollama 0.40.16) embedded a German query of 80 characters 20 times and an English chunk of
1500 characters 10 times per model, with both instances and Postgres running. Memory is the Ollama container's with
the model loaded. The Re-Index estimate multiplies the chunk median by the about 26,900 chunks of both Collections,
one request after another.

| Model | Query, median | Chunk, median | First load | Ollama memory | Re-Index of both Collections |
| --- | --- | --- | --- | --- | --- |
| `bge-m3` | 116 ms | 1.6 s | 9.5 s | 1.3 GB | about 12 hours |
| `qwen3-embedding:0.6b` | 135 ms | 1.4 s | 7.0 s | 1.2 GB | about 10 hours |
| `qwen3-embedding:4b` | 1124 ms | 11.6 s | 36 s | 3.1 GB | about 3.5 days |

`snowflake-arctic-embed2` was not measured on the Pi, since it is not ahead of `qwen3-embedding:0.6b` on either
Collection.
