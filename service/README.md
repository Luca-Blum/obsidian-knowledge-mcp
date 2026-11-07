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
| `collection.search.tool-description` | `COLLECTION_SEARCH_TOOL_DESCRIPTION` | Description Claude reads for `search` |
| `collection.search.default-k` | `COLLECTION_SEARCH_DEFAULT_K` | Search Hits when `k` is not given (default `5`) |
| `collection.search.max-k` | `COLLECTION_SEARCH_MAX_K` | Largest `k` a caller may ask for (default `20`) |

In the `prod` profile the database and Ollama come from `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` and
`OLLAMA_BASE_URL`; `/q/health/ready` reports readiness. See [`deploy/`](../deploy/README.md).

In development, Ollama is expected at `http://localhost:11434` (`quarkus.langchain4j.ollama.base-url`).

## Run locally and connect Claude Code

```shell
ollama pull bge-m3      # or: docker run -d -p 11434:11434 --name ollama ollama/ollama && docker exec ollama ollama pull bge-m3
COLLECTION_VAULT_PATH=/path/to/Engineering ./service/mvnw -f service/pom.xml quarkus:dev
```

The `dev` profile configures the engineering Collection on port 8081 and indexes the Vault in the background after
start-up. Then connect Claude Code:

```shell
claude mcp add --transport http knowledge-engineering http://localhost:8081/mcp
```

## Tests

```shell
cd service && ./mvnw test
```

Tests write Notes into a temporary Vault (`target/test-vault`), index it and search against real Postgres with
pgvector. A deterministic bag-of-words fake replaces the embedding model.
