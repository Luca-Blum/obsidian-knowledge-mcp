# Knowledge service

One instance serves one Collection. It indexes the Collection's Vault into its own Postgres schema and offers
`search`, `read_note`, `list_topics` and `list_sources` over MCP (Streamable HTTP, `/mcp`). See [`docs/architecture.md`](../docs/architecture.md).

Requires Java 25+ and Docker (Dev Services start Postgres with pgvector).

## Code structure

The service is a hexagon ([ADR 0003](../docs/adr/0003-ports-are-interfaces-and-roles-are-annotated.md)), packaged by
layer and then by concept ([ADR 0004](../docs/adr/0004-packages-by-layer-then-concept.md)), under
`src/main/java/io/github/lucablum/knowledge/`:

| Package | Holds | Start reading at |
| --- | --- | --- |
| `domain.note`, `domain.markdown` | What a Note is; its lines, headings and heading paths | `Note`, `MarkdownLines` |
| `domain.chunk` | How a Note becomes Chunks | `Splitter`, `ChunkingRules` |
| `domain.search`, `domain.link`, `domain.reading`, `domain.catalog` | What the AI Agent gets back | `ReciprocalRankFusion`, `SearchHit`, `Link` |
| `application.indexing` | Reconciliation and Re-Index | `ContentHashReconciliation`, `FingerprintReIndex` |
| `application.search` | Hybrid search with Reciprocal Rank Fusion | `HybridSearch` |
| `application.reading`, `.link`, `.catalog` | `read_note`, `related` and the lists | `ReadNote`, `QueryTimeResolution` |
| `adapter.mcp` | The MCP tools | `SearchTool` |
| `adapter.postgres` | Every secondary port of the index, on pgvector and `tsvector` | `PostgresIndex` |
| `adapter.ollama`, `.filesystem`, `.reconciliation`, `.http`, `.health` | Embedding, Vault, scheduling, auth, readiness | `OllamaEmbedding`, `FileSystemVault` |
| `adapter.config` | Configuration and the wiring of use cases | `CollectionConfig`, `UseCases` |

`ArchitectureTest` checks these rules on every build.

## Configuration

A Collection is defined only by configuration:

### Collection

| Property | Environment variable | Meaning |
| --- | --- | --- |
| `collection.name` | `COLLECTION_NAME` | Name of the Collection, e.g. `engineering` |
| `collection.vault-path` | `COLLECTION_VAULT_PATH` | Vault root, read only |
| `collection.schema` | `COLLECTION_SCHEMA` | Postgres schema of the index |
| `collection.mcp-port` | `COLLECTION_MCP_PORT` | HTTP port of the MCP endpoint (default `8081`) |
| `collection.default-language` | `COLLECTION_DEFAULT_LANGUAGE` | Note Language when a Note's `lang` is missing or unknown: `de` or `en` (default `en`) |

### Embedding

| Property | Environment variable | Meaning |
| --- | --- | --- |
| `collection.embedding.model` | `COLLECTION_EMBEDDING_MODEL` | Ollama embedding model (default `bge-m3`) |
| `collection.embedding.dimension` | `COLLECTION_EMBEDDING_DIMENSION` | Vector dimension of that model (default `1024`) |

### Chunking

| Property | Environment variable | Meaning |
| --- | --- | --- |
| `collection.chunking.heading-level` | `COLLECTION_CHUNKING_HEADING_LEVEL` | Deepest heading level that starts a new Chunk; deeper headings stay inside it (default `3`, so H2 and H3) |
| `collection.chunking.minimum-characters` | `COLLECTION_CHUNKING_MINIMUM_CHARACTERS` | A section shorter than this is merged with a neighbour (default `200`) |
| `collection.chunking.maximum-characters` | `COLLECTION_CHUNKING_MAXIMUM_CHARACTERS` | A section longer than this is split at paragraphs; code blocks and tables never are (default `2000`) |
| `collection.chunking.overlap-characters` | `COLLECTION_CHUNKING_OVERLAP_CHARACTERS` | End of a split piece the next piece repeats, in whole words (default `100`) |

### Search

| Property | Environment variable | Meaning |
| --- | --- | --- |
| `collection.search.tool-description` | `COLLECTION_SEARCH_TOOL_DESCRIPTION` | Description the AI Agent reads for `search` |
| `collection.search.default-k` | `COLLECTION_SEARCH_DEFAULT_K` | Search Hits when `k` is not given (default `5`) |
| `collection.search.max-k` | `COLLECTION_SEARCH_MAX_K` | Largest `k` a caller may ask for (default `20`) |
| `collection.search.minimum-similarity` | `COLLECTION_SEARCH_MINIMUM_SIMILARITY` | Cosine similarity a vector candidate must reach before the fusion; full-text candidates are not thresholded (default `0.5`, uncalibrated, belongs to the embedding model) |
| `collection.search.max-hits-per-note` | `COLLECTION_SEARCH_MAX_HITS_PER_NOTE` | Search Hits one Note may take; each hit's `moreInNote` counts the rest (default `2`) |
| `collection.search.candidates` | `COLLECTION_SEARCH_CANDIDATES` | Candidates each list contributes to the fusion, the closest Chunks of the vector search and the best ranked of the full-text search, before the cap and `k` (default `50`) |
| `collection.search.fusion.k` | `COLLECTION_SEARCH_FUSION_K` | The `k` of Reciprocal Rank Fusion, `weight / (k + rank)`; larger flattens the top ranks (default `60`) |
| `collection.search.fusion.vector-weight` | `COLLECTION_SEARCH_FUSION_VECTOR_WEIGHT` | Weight of the vector list in the fusion (default `1.0`) |
| `collection.search.fusion.full-text-weight` | `COLLECTION_SEARCH_FUSION_FULL_TEXT_WEIGHT` | Weight of the full-text list in the fusion; `0` leaves it out, which is plain vector search (default `1.0`) |

### Reading

| Property | Environment variable | Meaning |
| --- | --- | --- |
| `collection.reading.maximum-characters` | `COLLECTION_READING_MAXIMUM_CHARACTERS` | Longest Note `read_note` returns in full; a longer one comes back as an outline and its beginning (default `8000`) |
| `collection.reading.beginning-characters` | `COLLECTION_READING_BEGINNING_CHARACTERS` | How much of a long Note's start comes with its outline (default `2000`) |

### Reconciliation

| Property | Environment variable | Meaning |
| --- | --- | --- |
| `collection.reconciliation.interval` | `COLLECTION_RECONCILIATION_INTERVAL` | How often the Vault is compared with the index (default `5m`) |
| `collection.reconciliation.quiet-period` | `COLLECTION_RECONCILIATION_QUIET_PERIOD` | How long the Vault must stay quiet after a file change before it is reconciled (default `2s`) |

In the `prod` profile the database and Ollama come from `DATABASE_URL`, `DATABASE_PASSWORD` and `OLLAMA_BASE_URL`. The
service logs in as the role named like `collection.schema`, which sees only that schema; `/q/health/ready` reports
readiness. See [`deploy/`](../deploy/README.md).

## Re-Index

The index records its fingerprint: the embedding model, the dimension and the four `collection.chunking` values. At
start-up, before the first Reconciliation, the service compares it with the configuration and on any difference
re-indexes the whole Vault, so changing the model or the chunking rules is a configuration change only. An index
without a fingerprint, such as one from before fingerprints were stored, is re-indexed too.

While a Re-Index runs, `search` answers with an error that says how far it has come (`12 of 340 Notes`), and the
`Re-Index` check of `/q/health/ready` reports `state` `running` with the same `progress`. The instance stays ready,
as `read_note`, `related` and the lists keep working.

Until the index carries the configured fingerprint, it counts as being re-indexed, also before the Re-Index starts
and after one failed (`progress` then reads `not started yet`). Reconciliation never runs on such an index: each run
tries the Re-Index again first. A Re-Index in which some Notes failed still completes, as those Notes hold no Chunk
of the old model and the next Reconciliation indexes them; only one in which every Note failed, such as when Ollama
is unreachable, does not. `POST /admin/re-index` forces a Re-Index (`202`, or `409` while
one runs). Like the MCP endpoint, it needs a client token:

```shell
curl -X POST -H "Authorization: Bearer dev-token" http://localhost:8081/admin/re-index
```

In development, Ollama is expected at `http://localhost:11434` (`quarkus.langchain4j.ollama.base-url`).

## Run locally and connect Claude Code

```shell
ollama pull bge-m3      # or: docker run -d -p 11434:11434 --name ollama ollama/ollama && docker exec ollama ollama pull bge-m3
COLLECTION_VAULT_PATH=/path/to/Engineering ./service/mvnw -f service/pom.xml quarkus:dev
```

The `dev` profile configures the engineering Collection on port 8081. In the background the service reconciles the
Vault with the index: right after start-up, every `collection.reconciliation.interval`, and seconds after a file
changes. Only Notes whose content hash differs from the indexed one are embedded again, so a restart is cheap,
unless the configuration changed the index fingerprint (see [Re-Index](#re-index)). Then connect Claude Code with the
`dev` profile's client token:

```shell
claude mcp add --transport http knowledge-engineering http://localhost:8081/mcp --header "Authorization: Bearer dev-token"
```

## Authentication

Every request, `/mcp` and `/admin` included, needs `Authorization: Bearer <token>` with the token of a configured
Client; anything else gets `401`. Only health (`/q/health`) stays open. Each client has its own token per Collection, set only in the
environment as `COLLECTION_AUTH_TOKENS_<CLIENT>`, for example `COLLECTION_AUTH_TOKENS_CLAUDE_CODE`. Removing one, or
leaving it empty, locks out that client alone. An instance with no token at all refuses to start.

## Tests

```shell
cd service && ./mvnw test
```

Tests write Notes into a temporary Vault (`target/test-vault`), index it and search against real Postgres with
pgvector. A deterministic bag-of-words fake replaces the embedding model.

## Evaluation

How well a Collection's search finds the right Notes is measured with its Evaluation Set, on the Mac with the real
embedding model and never in the normal build. The method, how to run it and the results are in
[`docs/evaluation/`](../docs/evaluation/README.md).
