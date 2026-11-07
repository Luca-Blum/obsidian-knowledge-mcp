# Deploy to the Raspberry Pi

`docker-compose.yml` runs Postgres with pgvector, Ollama and `knowledgebase-engineering` on the Pi. The Vault is synced by
Syncthing and mounted read-only into `knowledgebase-engineering` only.

## First setup

```shell
cp .env.example .env    # set POSTGRES_PASSWORD and ENGINEERING_VAULT_DIR; the rest has working defaults
docker login ghcr.io    # only if the image is private
docker compose up -d
```

`ollama-pull` downloads the embedding model once. `knowledgebase-engineering` waits for healthy Postgres and Ollama, and its own
health check (`/q/health/ready`) reports ready once the datasource works.

## Update

```shell
docker compose pull && docker compose up -d
```

CI builds the arm64 image on every push to `main` and publishes it to
`ghcr.io/luca-blum/obsidian-knowledge-mcp` (tags `latest` and the commit SHA).

## Memory

Heap is bounded by `ENGINEERING_HEAP` (default `-Xmx512m`) and the container by `mem_limit: 900m`, which leaves room
for Postgres and Ollama in 8 GB.

## Connect Claude Code from the Mac

```shell
claude mcp add --transport http knowledge-engineering http://<pi-tailscale-name>:8081/mcp
```
