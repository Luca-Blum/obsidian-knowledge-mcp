# obsidian-knowledge-mcp

[![CI](https://github.com/Luca-Blum/obsidian-knowledge-mcp/actions/workflows/ci.yml/badge.svg)](https://github.com/Luca-Blum/obsidian-knowledge-mcp/actions/workflows/ci.yml)
[![Deploy](https://github.com/Luca-Blum/obsidian-knowledge-mcp/actions/workflows/deploy.yml/badge.svg)](https://github.com/Luca-Blum/obsidian-knowledge-mcp/actions/workflows/deploy.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
![Java 25](https://img.shields.io/badge/Java-25-orange.svg)
![Quarkus](https://img.shields.io/badge/Quarkus-LangChain4j-4695EB.svg)

A self-hosted retrieval service that turns Obsidian Vaults into isolated knowledge bases and lets an AI Agent search
them over MCP. Hybrid search (pgvector + Postgres full text), Java 25, Quarkus, LangChain4j. It runs on a Raspberry
Pi 5 and deploys there on every merge.

![Architecture overview](docs/images/social-preview.png)

## Why

My notes and sources live in Obsidian: books converted from PDF, course material, my own notes linking them. I want an
AI Agent to answer questions from exactly these sources and cite them, without a cloud service, without usage
limits, and without the Vault ever leaving my control.

The service does **Agentic RAG**: it only retrieves. The AI Agent decides when to search, which Notes to read in
full and which Links to follow, and writes the answer itself. No language model runs on the Pi.

## Highlights

- **Hexagonal architecture, enforced.** Domain and use cases are plain Java; Quarkus, LangChain4j and Postgres appear
  only in adapters. Every type carries its role as a jMolecules annotation, and ArchUnit tests fail the build when a
  dependency points the wrong way ([ADR 0003](docs/adr/0003-ports-are-interfaces-and-roles-are-annotated.md),
  [ADR 0004](docs/adr/0004-packages-by-layer-then-concept.md)).
- **Hybrid search with a relevance cut-off that works.** Vector and full-text rankings are merged with Reciprocal
  Rank Fusion. Because fused scores carry no notion of relevance, the Minimum Similarity is applied to the vector list
  *before* the fusion ([ADR 0002](docs/adr/0002-minimum-similarity-before-fusion.md)).
- **Measured, not guessed.** Every Collection has an Evaluation Set of real questions and Negative Questions. Each
  change to search moves one lever and is measured against the baseline ([evaluation](docs/evaluation/README.md)).
- **Tools designed for AI Agents.** Separate parameters for semantic `query` and exact `keywords`, per-Collection
  tool descriptions, Search Hits capped per Note, and calibrated abstention: an off-topic question gets no hits
  instead of the least bad match.
- **A self-healing index.** Reconciliation compares content hashes with the Vault on start-up, periodically and after
  file changes, and embeds only what changed. A changed embedding model or chunking rule triggers a full Re-Index on
  its own, so swapping the model is a configuration change.
- **Isolated Collections.** One image, one instance per Collection, each with its own Postgres schema and database
  role, its own Vault mounted read-only, its own bearer tokens and its own public HTTPS origin.
- **Continuous deployment to a Raspberry Pi.** GitHub Actions tests and builds a native arm64 image; a self-hosted
  runner on the Pi pulls it and waits until every instance is healthy. The Pi opens no port.
- **Two languages, one system.** The Java service on the Pi and a Python importer on the Mac that converts PDFs,
  EPUBs, Word and a dozen other formats into Source Notes with [Docling](https://github.com/docling-project/docling).

## Results

Hit@5 on the Engineering Collection (16 questions, `bge-m3`): from **0.88** with plain vector search to **1.00**
with hybrid search, once the questions carry the exact terms the AI Agent passes as `keywords`.

| Configuration | Hit@5 | Recall@5 | MRR |
| --- | --- | --- | --- |
| Plain vector search ([baseline](docs/evaluation/engineering-baseline.md)) | 0.88 | 0.75 | 0.80 |
| Hybrid search, full-text weight 1.0, with `keywords` ([details](docs/evaluation/engineering-hybrid-search.md)) | 1.00 | 0.91 | 0.92 |

Four embedding models on extended Evaluation Sets with harder questions (41 and 45 questions, 16 Negative Questions
each), every model at its own calibrated Minimum Similarity. `qwen3-embedding:0.6b` finds two questions more than
`bge-m3` on both Collections at the same cost on the Pi; `qwen3-embedding:4b` is best on engineering but takes ten
times as long per query on the Pi ([comparison](docs/evaluation/embedding-models.md)).

| Model | Engineering Hit@5 | Training Hit@5 | Query on the Pi |
| --- | --- | --- | --- |
| `bge-m3` | 0.85 | 0.91 | 116 ms |
| `snowflake-arctic-embed2` | 0.90 | 0.91 | – |
| `qwen3-embedding:0.6b` | 0.90 | 0.96 | 135 ms |
| `qwen3-embedding:4b` | 0.98 | 0.93 | 1124 ms |

The same measurement also showed what hybrid search costs without `keywords`: the whole question as full-text query
pushes long book chapters into the top 5. That is why the `search` tool asks the AI Agent for exact terms separately.

## How it works

```mermaid
flowchart LR
    subgraph Mac
        importer["Importer<br/><small>Docling: PDF → Markdown</small>"]
        vaults["Obsidian Vaults"]
        importer --> vaults
    end
    subgraph Pi["Raspberry Pi 5"]
        instances["Knowledge service<br/><small>one instance per Collection</small>"]
        pg["Postgres + pgvector<br/><small>one schema per Collection</small>"]
        ollama["Ollama<br/><small>embedding model</small>"]
        instances <--> pg
        instances <--> ollama
    end
    agents["AI Agents<br/><small>Claude Code, claude.ai</small>"]
    vaults -- "Syncthing, one way, read-only" --> instances
    agents <-- "MCP over HTTPS, bearer token" --> instances
```

1. Notes are written on the Mac. The importer turns documents dropped into a Topic's `_inbox/` into Source Notes.
2. Syncthing mirrors each Vault one way to the Pi. Obsidian stays the only source of truth; the index can be
   deleted and rebuilt at any time.
3. Each instance parses its Vault's Markdown, cuts it into Chunks along headings, embeds them with Ollama and stores
   vectors, full text and Links in its own schema.
4. The AI Agent calls the MCP tools:

| Tool | What the AI Agent gets |
| --- | --- |
| `search` | Search Hits: the matching Chunks with Note path, heading path and Note Kind |
| `read_note` | A whole Note, or one section by its heading path |
| `related` | Links, Backlinks, and Links that resolve to nothing or to several Notes |
| `list_topics`, `list_sources` | What a Collection holds |

The full design, with its trade-offs, is in [`docs/architecture.md`](docs/architecture.md).

## Tech stack

| Area | Choice |
| --- | --- |
| Service | Java 25, Quarkus, Quarkus LangChain4j, `quarkus-mcp-server-http` (Streamable HTTP) |
| Architecture | Hexagonal, jMolecules, ArchUnit |
| Storage and search | Postgres 17, pgvector, `tsvector` full text with German and English stemming |
| Embeddings | Ollama, `qwen3-embedding:0.6b` (1024 dimensions, multilingual), chosen by evaluation, replaceable through configuration |
| Importer | Python, uv, Docling, Lingua for language detection, launchd |
| Tests | JUnit, ArchUnit, Quarkus Dev Services with real Postgres and pgvector, pytest |
| Operations | Docker Compose, GitHub Actions, GHCR, self-hosted runner, Tailscale Funnel, Syncthing |

## Quickstart

You need Java 25, Docker and [Ollama](https://ollama.com). Quarkus Dev Services start Postgres with pgvector.

```shell
ollama pull qwen3-embedding:0.6b
COLLECTION_VAULT_PATH=/path/to/your/vault ./service/mvnw -f service/pom.xml quarkus:dev
claude mcp add --transport http knowledge http://localhost:8081/mcp --header "Authorization: Bearer dev-token"
```

The last line connects Claude Code; any MCP client that can send a header works. Configuration, tests and the
Re-Index are described in [`service/README.md`](service/README.md).

## Repository

| Folder | What it holds |
| --- | --- |
| [`service/`](service/README.md) | The Quarkus knowledge service |
| [`importer/`](importer/README.md) | The Python importer that runs on the Mac |
| [`deploy/`](deploy/README.md) | Docker Compose, Postgres isolation and the Raspberry Pi setup |
| [`docs/`](docs/architecture.md) | Architecture, ADRs, evaluation results, ideas |
| [`.github/workflows/`](.github/workflows) | CI on every pull request, deploy on every merge to `main` |

## Documentation

- [Architecture](docs/architecture.md): goals, components, flows, retrieval design and trade-offs
- [Architecture Decision Records](docs/adr/README.md)
- [Glossary](GLOSSARY.md): the domain language used in code, docs and tool descriptions
- [Evaluation](docs/evaluation/README.md): method and results
- [Deployment](deploy/README.md): Raspberry Pi setup, operations and troubleshooting
- [Agentic engineering workflow](docs/agentic-development.md): specs, context engineering, guardrails, review gate
- [Ideas](docs/ideas.md): what was deliberately not built yet, and when to look again

## Agentic engineering workflow

Built with coding agents in a spec-driven, human-in-the-loop workflow:

- **Spec-driven:** every change starts as an issue with acceptance criteria; only issues triaged `ready-for-agent`
  go to an agent, and plans are stress-tested before implementation.
- **Context engineering:** agent instructions, a ubiquitous-language glossary and ADRs are versioned with the code,
  and recurring review findings become rules there.
- **Skills over prompts:** TDD, domain modeling, plan grilling and two-axis code review (standards and spec) run as
  versioned procedures from [mattpocock/skills](https://github.com/mattpocock/skills).
- **Guardrails:** ArchUnit architecture rules, integration tests on real Postgres, an eval suite for retrieval
  quality, CI on every pull request and health-gated deployment.
- **Human review gate:** every change reaches `main` through a pull request I review.

Details and examples: [`docs/agentic-development.md`](docs/agentic-development.md).

## Roadmap

- A re-ranker on the top candidates, if the Pi has the headroom.
- `qwen3-embedding:4b` once the Collections run on hardware with a GPU
  ([ADR 0006](docs/adr/0006-qwen3-embedding-0.6b-on-the-pi.md)).
- More in [Ideas](docs/ideas.md), each with what would make it worth building.

## License

[Apache License 2.0](LICENSE)
