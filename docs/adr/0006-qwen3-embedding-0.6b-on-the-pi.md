# The Collections on the Pi switch to qwen3-embedding:0.6b

Status: Accepted · 2026-11-16

The Collections on the Pi switch from `bge-m3` to `qwen3-embedding:0.6b`. `bge-m3` was the first model tried, never
measured against others. On the extended Evaluation Sets of #54, each model at its own calibrated Minimum Similarity,
`qwen3-embedding:0.6b` finds two questions more than `bge-m3` on both Collections (37 against 35 of 41 on engineering,
43 against 41 of 45 on training), and costs the same on the Pi: 135 ms against 116 ms per query, 1.2 GB against 1.3 GB
of Ollama memory. The numbers are in [`embedding-models.md`](../evaluation/embedding-models.md).

The defaults in `deploy/docker-compose.yml`, `deploy/.env.example` and `application.properties` become
`qwen3-embedding:0.6b` with the Minimum Similarities calibrated for it, 0.61 for engineering and 0.53 for training. The
Pi switches when its `.env` takes the same values; the next deploy then starts a Re-Index of both Collections, about
10 hours on the Pi.

## Considered Options

- **Keep `bge-m3`.** It is ahead of no other model on either Collection. It also has no margin around its Minimum
  Similarity: its near-miss Negative Questions reach the same similarity as some real questions, so every value that
  keeps them out loses real questions. The Pi's current values, 0.56 and 0.60, let 3 and 1 of the 16 Negative
  Questions through.
- **`qwen3-embedding:4b`.** The best model on engineering (40 of 41) with the widest margin, but on training it ties
  with `qwen3-embedding:0.6b`. On the Pi it takes 1.1 s per query, 3.1 GB of memory and about 3.5 days for a Re-Index.
  Worth it once the Collections run on hardware with a GPU: on the Mac's GPU it indexed both Collections in under five
  hours.
- **`snowflake-arctic-embed2`.** Ties with `qwen3-embedding:0.6b` on engineering, falls behind it on training, and is
  the only model that misses a cross-language question even without a Minimum Similarity.

## Consequences

- The Minimum Similarity is tied to the model: the switch takes the values calibrated for `qwen3-embedding:0.6b`, and
  every later switch needs its own calibration.
- The evaluation ran on Ollama 0.12.3, the Pi runs 0.40.16. After the switch, a run against the Pi's index confirms
  the calibrated values.
- On engineering, a single near-miss Negative Question sets the Minimum Similarity: it reaches 0.601, and keeping it
  out costs three questions.
