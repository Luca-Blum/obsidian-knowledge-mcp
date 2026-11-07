# Deploy to the Raspberry Pi

`docker-compose.yml` runs Postgres with pgvector, Ollama and `knowledgebase-engineering` on the Pi. The Vault is synced by
Syncthing and mounted read-only into `knowledgebase-engineering` only.

## First setup on the Pi

1. Put the configuration where the Deploy workflow looks for it:
   ```shell
   sudo mkdir -p /srv/knowledgebase && sudo chown $USER /srv/knowledgebase
   cp deploy/.env.example /srv/knowledgebase/.env   # set POSTGRES_PASSWORD and ENGINEERING_VAULT_DIR; the rest has working defaults
   ```
2. Install a self-hosted runner for this repository (GitHub → Settings → Actions → Runners → New self-hosted runner,
   Linux ARM64). Give it the label `pi`, run it as a user in the `docker` group, and install it as a service
   (`sudo ./svc.sh install && sudo ./svc.sh start`).
3. Push to `main`, or rerun the latest Deploy workflow.

`ollama-pull` downloads the embedding model once. Each instance waits for healthy Postgres and Ollama, and its own
health check (`/q/health/ready`) reports ready once the datasource works.

## Deploy

Every push to `main` runs the Deploy workflow:

1. `image` tests the service, builds the arm64 image and publishes it to `ghcr.io/luca-blum/obsidian-knowledge-mcp`
   (tags `latest` and the commit SHA).
2. `deploy` runs on the Pi and starts every instance on the image of that commit with `docker compose up --wait`.
   The job fails if an instance does not become healthy within 10 minutes.

To roll back, rerun the Deploy workflow of an earlier commit. To deploy by hand from a clone on the Pi:

```shell
docker login ghcr.io
IMAGE=ghcr.io/luca-blum/obsidian-knowledge-mcp:<commit-sha> \
  docker compose --file deploy/docker-compose.yml --env-file /srv/knowledgebase/.env up --detach --pull always --wait
```

## Memory

Heap is bounded by `ENGINEERING_HEAP` (default `-Xmx512m`) and the container by `mem_limit: 900m`, which leaves room
for Postgres and Ollama in 8 GB.

## Connect Claude Code from the Mac

```shell
claude mcp add --transport http knowledge-engineering http://<pi-tailscale-name>:8081/mcp
```
