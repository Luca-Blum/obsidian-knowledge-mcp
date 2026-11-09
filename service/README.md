# Knowledge service

One instance serves one Collection. It indexes the Collection's Vault into its own Postgres schema and offers
`search`, `list_topics` and `list_sources` over MCP (Streamable HTTP, `/mcp`). See [`docs/architecture.md`](../docs/architecture.md).

Requires Java 25+ and Docker (Dev Services start Postgres with pgvector).

## Configuration

A Collection is defined only by configuration:

| Property | Environment variable | Meaning |
| --- | --- | --- |
| `collection.name` | `COLLECTION_NAME` | Name of the Collection, e.g. `engineering` |
| `collection.vault-path` | `COLLECTION_VAULT_PATH` | Vault root, read only |
| `collection.schema` | `COLLECTION_SCHEMA` | Postgres schema of the index |
| `collection.mcp-port` | `COLLECTION_MCP_PORT` | HTTP port of the MCP endpoint (default `8081`) |
| `collection.default-language` | `COLLECTION_DEFAULT_LANGUAGE` | Note Language when a Note's `lang` is missing or unknown: `de` or `en` (default `en`) |
| `collection.embedding.model` | `COLLECTION_EMBEDDING_MODEL` | Ollama embedding model (default `bge-m3`) |
| `collection.embedding.dimension` | `COLLECTION_EMBEDDING_DIMENSION` | Vector dimension of that model (default `1024`) |
| `collection.chunking.heading-level` | `COLLECTION_CHUNKING_HEADING_LEVEL` | Deepest heading level that starts a new Chunk; deeper headings stay inside it (default `3`, so H2 and H3) |
| `collection.chunking.minimum-characters` | `COLLECTION_CHUNKING_MINIMUM_CHARACTERS` | A section shorter than this is merged with a neighbour (default `200`) |
| `collection.chunking.maximum-characters` | `COLLECTION_CHUNKING_MAXIMUM_CHARACTERS` | A section longer than this is split at paragraphs; code blocks and tables never are (default `2000`) |
| `collection.chunking.overlap-characters` | `COLLECTION_CHUNKING_OVERLAP_CHARACTERS` | End of a split piece the next piece repeats, in whole words (default `100`) |
| `collection.search.tool-description` | `COLLECTION_SEARCH_TOOL_DESCRIPTION` | Description Claude reads for `search` |
| `collection.search.default-k` | `COLLECTION_SEARCH_DEFAULT_K` | Search Hits when `k` is not given (default `5`) |
| `collection.search.max-k` | `COLLECTION_SEARCH_MAX_K` | Largest `k` a caller may ask for (default `20`) |
| `collection.search.minimum-similarity` | `COLLECTION_SEARCH_MINIMUM_SIMILARITY` | Cosine similarity a Chunk must reach to be a Search Hit at all (default `0.5`, uncalibrated, belongs to the embedding model) |
| `collection.search.max-hits-per-note` | `COLLECTION_SEARCH_MAX_HITS_PER_NOTE` | Search Hits one Note may take; each hit's `moreInNote` counts the rest (default `2`) |
| `collection.search.candidates` | `COLLECTION_SEARCH_CANDIDATES` | Closest Chunks a search looks at before the cap and `k` (default `50`) |
| `collection.reconciliation.interval` | `COLLECTION_RECONCILIATION_INTERVAL` | How often the Vault is compared with the index (default `5m`) |
| `collection.reconciliation.quiet-period` | `COLLECTION_RECONCILIATION_QUIET_PERIOD` | How long the Vault must stay quiet after a file change before it is reconciled (default `2s`) |

In the `prod` profile the database and Ollama come from `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` and
`OLLAMA_BASE_URL`; `/q/health/ready` reports readiness. See [`deploy/`](../deploy/README.md).

In development, Ollama is expected at `http://localhost:11434` (`quarkus.langchain4j.ollama.base-url`).

## Run locally and connect Claude Code

```shell
ollama pull bge-m3      # or: docker run -d -p 11434:11434 --name ollama ollama/ollama && docker exec ollama ollama pull bge-m3
COLLECTION_VAULT_PATH=/path/to/Engineering ./service/mvnw -f service/pom.xml quarkus:dev
```

The `dev` profile configures the engineering Collection on port 8081. In the background the service reconciles the
Vault with the index: right after start-up, every `collection.reconciliation.interval`, and seconds after a file
changes. Only Notes whose content hash differs from the indexed one are embedded again, so a restart is cheap. Then connect Claude Code:

```shell
claude mcp add --transport http knowledge-engineering http://localhost:8081/mcp
```

## Tests

```shell
cd service && ./mvnw test
```

Tests write Notes into a temporary Vault (`target/test-vault`), index it and search against real Postgres with
pgvector. A deterministic bag-of-words fake replaces the embedding model.

## Evaluation

The evaluation measures how well a Collection's search finds the right Notes. It runs on the Mac with the real
embedding model, never in the normal build. Each Collection's Evaluation Set lives in its own Vault, in a folder that
is never indexed, e.g. `Engineering/_eval/questions.yaml`. Questions never go into this repository.

```yaml
questions:
  - question: What does the Dependency Rule say?
    topic: Software-Engineering          # optional, restricts the search like the tool's 'topic'
    expected:                            # Notes that answer it, as paths relative to the Vault root
      - Software-Engineering/Source-Notes/clean-architecture-ch5.md
negative-questions:                      # questions this Collection must not answer
  - question: How should I taper before a marathon?
```

Start Ollama with the model the Pi uses (`EMBEDDING_MODEL` in `deploy/.env`, default `bge-m3`), then run:

```shell
cd service
COLLECTION_VAULT_PATH=/path/to/Engineering \
  ./mvnw test -Dtest=EvaluationRun -Devaluation.set=/path/to/Engineering/_eval/questions.yaml
```

`COLLECTION_EMBEDDING_MODEL` and `COLLECTION_EMBEDDING_DIMENSION` select another model, and `COLLECTION_NAME`
another Collection (default `engineering`). The run indexes the whole Vault into a Dev Services database, so the first
run takes as long as a Re-Index (about two hours for the engineering Vault on the Mac). Testcontainers removes that
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

A Negative Question passes when none of its hits reaches the configured Minimum Similarity
(`COLLECTION_SEARCH_MINIMUM_SIMILARITY`), and the summary counts how many passed. The search already drops every hit
below that value, so to see how close the Negative Questions come, run once with
`COLLECTION_SEARCH_MINIMUM_SIMILARITY=0`: the highest similarity any of them reached is where the Minimum Similarity
would have to start. Run again with that value to see in Hit@5 what it costs the real questions.

Measure a baseline first, then change exactly one lever and measure again. Commit each `summary.md` as
`docs/evaluation/<collection>-<what changed>.md`. The baseline,
[`docs/evaluation/engineering-baseline.md`](../docs/evaluation/engineering-baseline.md), is plain vector search
without a Minimum Similarity; reproduce it with the command above and `COLLECTION_SEARCH_MINIMUM_SIMILARITY=0`.
