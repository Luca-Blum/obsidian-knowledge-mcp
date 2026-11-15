# Evaluation

## Results

Each run below changes one lever against the one before and is measured on the same Evaluation Set. The questions
themselves stay in the Vault, so only the numbers are here.

| Run | What changed | Hit@5 | Recall@5 | MRR |
| --- | --- | --- | --- | --- |
| [Engineering baseline](engineering-baseline.md) | Plain vector search, `bge-m3`, no Minimum Similarity | 0.88 | 0.75 | 0.80 |
| [Engineering hybrid search](engineering-hybrid-search.md) | Full-text weight 1.0, questions without `keywords` | 0.81 | 0.72 | 0.71 |
| [Engineering hybrid search](engineering-hybrid-search.md#with-keywords) | Full-text weight 1.0, questions with `keywords` | 1.00 | 0.91 | 0.92 |

What the second and third row show: the full-text search only helps when it gets exact terms. Given the whole
question, it matches common words in long book chapters and pushes expected Notes out of the top 5. Given the term
the AI Agent passes as `keywords`, it finds every expected Note. That is why the `search` tool asks for `keywords`
separately from `query`.

## Method

The evaluation measures how well a Collection's search finds the right Notes. It runs on the Mac with the real
embedding model, never in the normal build. Each Collection's Evaluation Set lives in its own Vault, in a folder that
is never indexed, e.g. `Engineering/_eval/questions.yaml`. Questions never go into this repository.

```yaml
questions:
  - question: What does the Dependency Rule say?
    topic: Software-Engineering          # optional, restricts the search like the tool's 'topic'
    keywords: Dependency Rule            # optional, exact terms for the full-text search like the tool's 'keywords'
    expected:                            # Notes that answer it, as paths relative to the Vault root
      - Software-Engineering/Source-Notes/clean-architecture-ch5.md
negative-questions:                      # questions this Collection must not answer
  - question: How should I taper before a marathon?
    keywords: Tapering                   # optional, as above
```

Without `keywords` the full-text search gets the whole question, as it would when the AI Agent leaves them out.

Start Ollama with the model the Pi uses (`EMBEDDING_MODEL` in `deploy/.env`, default `bge-m3`), then run:

```shell
cd service
COLLECTION_VAULT_PATH=/path/to/Engineering \
  ./mvnw test -Dtest=EvaluationRun -Devaluation.set=/path/to/Engineering/_eval/questions.yaml
```

`COLLECTION_EMBEDDING_MODEL` and `COLLECTION_EMBEDDING_DIMENSION` select another model, and `COLLECTION_NAME`
another Collection (default `engineering`). The run indexes the whole Vault into a Dev Services database, so the first
run, and every run after a change of the model or the chunking rules, takes as long as a Re-Index (about two hours
for the engineering Vault on the Mac). Testcontainers removes that
database after the run unless you allow reuse with `testcontainers.reuse.enable=true` in
`~/.testcontainers.properties`; then the container stays up, later runs skip every unchanged Note and take seconds,
and `docker rm -f` removes it. The run asks each question for the top 5 Search Hits and writes two files to
`target/evaluation/`, or to `-Devaluation.report=<folder>`:

- `summary.md`: only the run and the numbers, safe to commit.
- `details.md`: every question with the rank of its first expected Note and the Search Hits it got, and every
  Negative Question, marked passed or failed, with its hits and their similarity. It stays local.

The metrics are counted on the Notes of the top 5 Search Hits as returned. A Note that fills several slots counts once
and leaves fewer slots for the others.

- **Hit@5** (main metric): share of questions with at least one expected Note in the top 5.
- **Recall@5**: mean share of a question's expected Notes found in the top 5.
- **MRR**: mean of 1 / rank of the first expected Note, 0 when none is in the top 5.

A Negative Question passes when none of its vector hits reaches the configured Minimum Similarity
(`COLLECTION_SEARCH_MINIMUM_SIMILARITY`), and the summary counts how many passed. Its full-text hits never fail it; the
summary counts the Negative Questions that got any, and the details list them apart, for information only. The search already drops every hit
below that value, so to see how close the Negative Questions come, run once with
`COLLECTION_SEARCH_MINIMUM_SIMILARITY=0`: the highest similarity any of them reached is where the Minimum Similarity
would have to start. Run again with that value to see in Hit@5 what it costs the real questions.

Measure a baseline first, then change exactly one lever and measure again. Commit each `summary.md` as
`docs/evaluation/<collection>-<what changed>.md`. The baseline,
[`engineering-baseline.md`](engineering-baseline.md), is plain vector search
without a Minimum Similarity; reproduce it with the command above, `COLLECTION_SEARCH_MINIMUM_SIMILARITY=0` and
`COLLECTION_SEARCH_FUSION_FULL_TEXT_WEIGHT=0`.
