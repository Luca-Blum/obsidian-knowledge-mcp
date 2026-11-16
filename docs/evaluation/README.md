# Evaluation

## Results

Each run below changes one lever against the one before and is measured on the same Evaluation Set. The questions
themselves stay in the Vault, so only the numbers are here.

| Run | What changed | Hit@5 | Recall@5 | MRR |
| --- | --- | --- | --- | --- |
| [Engineering baseline](engineering-baseline.md) | Plain vector search, `bge-m3`, no Minimum Similarity | 0.88 | 0.75 | 0.80 |
| [Engineering hybrid search](engineering-hybrid-search.md) | Full-text weight 1.0, questions without `keywords` | 0.81 | 0.72 | 0.71 |
| [Engineering hybrid search](engineering-hybrid-search.md#with-keywords) | Full-text weight 1.0, questions with `keywords` | 1.00 | 0.91 | 0.92 |
| [Engineering Minimum Similarity](engineering-minimum-similarity.md) | Minimum Similarity 0.56 | 1.00 | 0.97 | 0.94 |

The training Collection has its own Evaluation Set: [`training-minimum-similarity.md`](training-minimum-similarity.md)
measures it with hybrid search and chooses its Minimum Similarity.

For the comparison of embedding models (#54) both Evaluation Sets were extended to harder questions in several
categories and `near-miss` Negative Questions, so the runs below are not comparable to the rows above. Each model gets
its own Minimum Similarity, calibrated against the Negative Questions.

| Run | What changed | Hit@5 | Recall@5 | MRR |
| --- | --- | --- | --- | --- |
| [Engineering, `bge-m3`](engineering-bge-m3.md) | Extended set, 41 questions, Minimum Similarity 0.60 | 0.85 | 0.80 | 0.79 |
| [Training, `bge-m3`](training-bge-m3.md) | Extended set, 45 questions, Minimum Similarity 0.62 | 0.91 | 0.75 | 0.81 |
| [Engineering, `snowflake-arctic-embed2`](engineering-snowflake.md) | Model, Minimum Similarity 0.48 | 0.90 | 0.86 | 0.87 |
| [Training, `snowflake-arctic-embed2`](training-snowflake.md) | Model, Minimum Similarity 0.46 | 0.91 | 0.79 | 0.85 |
| [Engineering, `qwen3-embedding:0.6b`](engineering-qwen3-0.6b.md) | Model, Minimum Similarity 0.61 | 0.90 | 0.84 | 0.83 |
| [Training, `qwen3-embedding:0.6b`](training-qwen3-0.6b.md) | Model, Minimum Similarity 0.53 | 0.96 | 0.81 | 0.86 |
| [Engineering, `qwen3-embedding:4b`](engineering-qwen3-4b.md) | Model, Minimum Similarity 0.55 | 0.98 | 0.95 | 0.88 |
| [Training, `qwen3-embedding:4b`](training-qwen3-4b.md) | Model, Minimum Similarity 0.54 | 0.93 | 0.79 | 0.86 |

[`embedding-models.md`](embedding-models.md) compares the four models per category and across every Minimum
Similarity, adds their cost on the Pi, and leads to [ADR 0006](../adr/0006-qwen3-embedding-0.6b-on-the-pi.md):
`qwen3-embedding:0.6b` finds two questions more than `bge-m3` on both Collections at the same cost on the Pi.

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
    category: definition                 # the question's shape, see below
    topic: Software-Engineering          # optional, restricts the search like the tool's 'topic'
    keywords: Dependency Rule            # optional, exact terms for the full-text search like the tool's 'keywords'
    expected:                            # Notes that answer it, as paths relative to the Vault root
      - Software-Engineering/Source-Notes/clean-architecture-ch5.md
negative-questions:                      # questions this Collection must not answer
  - question: How should I taper before a marathon?
    category: other-collection           # other-collection or near-miss
    keywords: Tapering                   # optional, as above
```

Every question has exactly one `category`, so that a run shows where a model wins or loses, not only one average.
Where shapes overlap, the harder one is chosen, e.g. a German paraphrase of an English Note is `cross-language`.

| Category | Shape |
| --- | --- |
| `definition` | Asks what a named concept is. Kept, but a minority. |
| `paraphrase` | Describes a concept without naming it. |
| `scenario` | Describes a situation in several sentences and asks what to do. |
| `comparison` | Asks how two or more concepts differ or relate. |
| `multi-note` | Needs several Notes, e.g. a Source Note and an Own Note, or two books. |
| `cross-language` | Asked in German about an English Note, or the reverse. |
| `own-note` | Answered by an Own Note, not a Source Note. |

A Negative Question is either `other-collection`, a question that belongs to another Collection, or `near-miss`, a
question from the Collection's own field that its Vault does not cover.

Without `keywords` the full-text search gets the whole question, as it would when the AI Agent leaves them out.

Start Ollama with the model the Pi uses (`EMBEDDING_MODEL` in `deploy/.env`, default `qwen3-embedding:0.6b`), then run:

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

The summary repeats the three metrics per category, and counts the Negative Questions passed and their highest
similarity per kind.

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
