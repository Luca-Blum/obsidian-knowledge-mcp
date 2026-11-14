# Deploy to the Raspberry Pi

The knowledge service runs on a Raspberry Pi 5 (8 GB). Every push to `main` deploys it there automatically.

## What runs where

```
Mac                                    Raspberry Pi
───                                    ────────────
Obsidian + Vaults ──── Syncthing ────▶ /srv/vaults/Engineering, /srv/vaults/Training (read-only copies)
importer (launchd)                              │ each mounted read-only into its own instance
Claude Code  ── Tailscale, port 8081 ──▶ knowledgebase-engineering ──┬──▶ postgres (pgvector), one schema each
             ── Tailscale, port 8082 ──▶ knowledgebase-training ─────┴──▶ ollama (embedding model)
```

On the Pi everything runs in Docker, started from `docker-compose.yml`:

| Container | Image | Job |
| --- | --- | --- |
| `postgres` | `pgvector/pgvector:pg17` | Stores the index. Data in the Docker volume `knowledgebase_postgres`. |
| `postgres-init` | `pgvector/pgvector:pg17` | Gives each Collection its own database role that sees only its schema, then exits. |
| `ollama` | `ollama/ollama` | Computes embeddings. Models in the Docker volume `knowledgebase_ollama`. |
| `ollama-pull` | `ollama/ollama` | Downloads the embedding model (`bge-m3`) once, then exits. |
| `knowledgebase-engineering` | `ghcr.io/luca-blum/obsidian-knowledge-mcp` | The service for the Engineering Collection. Serves MCP on port 8081. |
| `knowledgebase-training` | `ghcr.io/luca-blum/obsidian-knowledge-mcp` | The service for the Training Collection. Serves MCP on port 8082. |

Both Collection services run the same image with the same settings (`x-collection` in `docker-compose.yml`). They
differ only in values: name, schema, port, Vault, tokens, Minimum Similarity and tool description.

You do not install Postgres, Ollama or Java on the Pi. Docker pulls them.

The Pi only reads the Vault. Notes are written on the Mac (by you in Obsidian, and by the importer), and Syncthing
copies them to the Pi.

## How a deploy works

`.github/workflows/deploy.yml` runs on every push to `main`:

1. **`image`** runs on a GitHub-hosted arm64 machine. It runs the service tests, builds the Docker image and pushes it
   to `ghcr.io/luca-blum/obsidian-knowledge-mcp` with the tags `latest` and the commit SHA.
2. **`deploy`** runs on the Pi itself, through a self-hosted GitHub Actions runner. It checks out the repository and
   runs `docker compose up` with the image of that commit and the configuration in `/srv/knowledgebase/.env`. Compose
   only restarts containers that changed. The job fails if a container is not healthy after 10 minutes.

GitHub never connects to the Pi. The runner on the Pi asks GitHub for jobs, so the Pi needs no open port.

## First setup on the Pi

You need Raspberry Pi OS (64-bit) with Docker, Tailscale and the GitHub CLI (`gh`, logged in with `gh auth login`).
Your user must be in the `docker` group.

### 1. Configuration

```shell
sudo mkdir -p /srv/knowledgebase /srv/vaults/Engineering /srv/vaults/Training
sudo chown -R $USER: /srv/knowledgebase /srv/vaults
cp deploy/.env.example /srv/knowledgebase/.env
sed -i "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$(openssl rand -hex 24)/" /srv/knowledgebase/.env
for COLLECTION in ENGINEERING TRAINING; do
  sed -i "s/^${COLLECTION}_DB_PASSWORD=.*/${COLLECTION}_DB_PASSWORD=$(openssl rand -hex 24)/" /srv/knowledgebase/.env
  sed -i "s/^${COLLECTION}_TOKEN_CLAUDE_CODE=.*/${COLLECTION}_TOKEN_CLAUDE_CODE=$(openssl rand -hex 32)/" /srv/knowledgebase/.env
  sed -i "s/^${COLLECTION}_TOKEN_CLAUDE_AI=.*/${COLLECTION}_TOKEN_CLAUDE_AI=$(openssl rand -hex 32)/" /srv/knowledgebase/.env
done
chmod 600 /srv/knowledgebase/.env
```

`.env.example` explains each value. The passwords are used only between containers on the Pi, so you never type them
anywhere. Keep the file: if you lose it, the Postgres volume can no longer be opened with a new password.

- `POSTGRES_PASSWORD` belongs to the superuser, which only `postgres-init` uses.
- `ENGINEERING_DB_PASSWORD` belongs to the role `engineering`. `postgres-init` creates it on every deploy (or sets its
  password again) and makes it the owner of the schema `engineering`, which no other Collection's role can read. The
  instance connects as this role.
- `ENGINEERING_TOKEN_<CLIENT>` is the bearer token one Client sends to the Engineering Collection. Every request
  without a configured token gets `401`, except health (`/q/health`). At least one token must be set, or the
  instance refuses to start.
- The `TRAINING_` values mean the same for the Training Collection and its role `training`. Its tokens differ from
  Engineering's, so a token of one Collection is never accepted by the other.

An existing installation needs the three new values in `.env` before its next deploy. `postgres-init` then hands the
schema that the superuser built to the role `engineering`, and the index stays as it is.

Before the deploy that adds the Training Collection, an existing installation also needs `TRAINING_DB_PASSWORD`, the
`TRAINING_` tokens and the other `TRAINING_` values from `.env.example`, and the Training Vault synced to
`/srv/vaults/Training`. Without them the deploy stops with `set in .env`.

### 2. GitHub Actions runner

```shell
VERSION=$(gh api repos/actions/runner/releases/latest --jq .tag_name | sed 's/^v//')
mkdir -p ~/actions-runner && cd ~/actions-runner
curl -fsSL "https://github.com/actions/runner/releases/download/v$VERSION/actions-runner-linux-arm64-$VERSION.tar.gz" | tar xz
TOKEN=$(gh api -X POST repos/Luca-Blum/obsidian-knowledge-mcp/actions/runners/registration-token --jq .token)
./config.sh --unattended --url https://github.com/Luca-Blum/obsidian-knowledge-mcp \
  --token "$TOKEN" --name "$(hostname)" --labels pi
sudo ./svc.sh install $USER && sudo ./svc.sh start
```

- The label `pi` is what `runs-on: [self-hosted, pi]` in the workflow looks for.
- `svc.sh install $USER` runs the runner as your user, so it can use Docker. It starts again after a reboot.
- The registration token is valid for one hour. You can also copy the commands from GitHub → Settings → Actions →
  Runners → New self-hosted runner (Linux, ARM64).

Check that GitHub sees it as `online`:

```shell
gh api repos/Luca-Blum/obsidian-knowledge-mcp/actions/runners --jq '.runners[] | "\(.name) \(.status)"'
```

### 3. Syncthing for the Vault

On the Pi, install Syncthing and print its Device ID:

```shell
sudo apt-get install -y syncthing
sudo systemctl enable --now syncthing@$USER
syncthing --device-id
```

On the Mac (`brew install --cask syncthing`, web UI at http://localhost:8384):

1. **Add Remote Device**: enter the Pi's Device ID.
2. Share each Vault folder with the Pi (folder → Edit → Sharing → tick the Pi). Note the folder's **Folder ID**
   (folder → Edit → General), for example `Engineering` and `Training`.
3. Copy the Mac's Device ID (Actions → Show ID).

Back on the Pi, add the Mac once, and then each folder. `<FOLDER-ID>` must be exactly the Folder ID from the Mac, or
Syncthing treats them as two different folders and nothing syncs. For the Training Vault, use the label `Training` and
the path `/srv/vaults/Training`:

```shell
syncthing cli config devices add --device-id <MAC-DEVICE-ID> --name mac
syncthing cli config folders add --id <FOLDER-ID> --label Engineering --path /srv/vaults/Engineering
syncthing cli config folders <FOLDER-ID> type set receiveonly
syncthing cli config folders <FOLDER-ID> ignore-perms set true
syncthing cli config folders <FOLDER-ID> devices add --device-id <MAC-DEVICE-ID>
```

- **Receive Only**: a change on the Pi is never sent back to the Mac.
- **Ignore permissions**: the importer creates Source Notes that only their owner may read (mode `600`). Without this
  setting Syncthing copies that mode to the Pi, and the service, which runs as a different user in its container,
  cannot read them. If files arrived before the setting was on, fix them once with `chmod -R a+rX /srv/vaults/<Vault>`.

`syncthing cli show pending folders` lists a folder the Mac offers that the Pi has not added, and shows its Folder ID.
The Pi's web UI only listens on the Pi itself. To open it from the Mac: `ssh -L 8384:localhost:8384 <user>@<pi>`, then
http://localhost:8384.

Check the sync with `syncthing cli show connections` (the Mac shows `"connected": true`) and `ls /srv/vaults/*`.

### 4. First deploy

Push to `main`, or rerun the latest Deploy workflow (GitHub → Actions → Deploy → Re-run all jobs). The first run takes
a few minutes longer, because `ollama-pull` downloads the embedding model (about 1.2 GB).

## Indexing

The service keeps the index in line with the Vault on its own (Reconciliation). Right after it starts, every five
minutes (`COLLECTION_RECONCILIATION_INTERVAL`) and a few seconds after Syncthing changes a file, it compares every Note's
content hash with the one in the index. It embeds only new and changed Notes and removes deleted ones; a rename is a
removal plus a new Note. A restart or deploy therefore re-embeds only what changed while the service was down.

Embedding runs one chunk at a time on the Pi's CPU and takes several seconds per chunk, so the very first run, or a
large batch of new Notes, takes roughly an hour per 4 MB of Markdown. Search works meanwhile, and a Note always shows
either its old or its new version, never a mix.

Progress, as the number of chunks in each index:

```shell
docker exec knowledgebase-postgres-1 psql -U knowledge -d knowledge -Atc \
  "select (select count(*) from engineering.chunk), (select count(*) from training.chunk)"
```

## Check that it runs

```shell
docker compose --project-name knowledgebase ps --all           # every container "healthy", ollama-pull "exited (0)"
curl http://<pi-tailscale-name>:8081/q/health/ready            # Engineering: {"status":"UP", ...}
curl http://<pi-tailscale-name>:8082/q/health/ready            # Training
docker logs --tail 50 knowledgebase-knowledgebase-engineering-1
docker stats --no-stream                                       # memory per container, see Memory
```

## Connect Claude Code from the Mac

The Mac and the Pi must be in the same Tailscale network. Each Collection is its own MCP server with its own token:

```shell
claude mcp add --transport http knowledge-engineering http://<pi-tailscale-name>:8081/mcp \
  --header "Authorization: Bearer <ENGINEERING_TOKEN_CLAUDE_CODE from .env>"
claude mcp add --transport http knowledge-training http://<pi-tailscale-name>:8082/mcp \
  --header "Authorization: Bearer <TRAINING_TOKEN_CLAUDE_CODE from .env>"
```

## Day to day

| Task | How |
| --- | --- |
| Deploy a change | Merge to `main`. |
| Roll back | GitHub → Actions → Deploy → pick the run of an earlier commit → Re-run all jobs. |
| Change configuration | Edit `/srv/knowledgebase/.env`, then rerun the latest Deploy workflow, or run the command below. |
| Lock out a client | Empty its `<COLLECTION>_TOKEN_<CLIENT>` in `.env` and deploy. To rotate, set a new token and add the connector again. |
| Force a Re-Index | `curl -X POST -H "Authorization: Bearer <token>" http://<pi-tailscale-name>:<port>/admin/re-index` |
| Deploy by hand | See below. |

To deploy by hand from a clone on the Pi (the `docker login` is only needed while the image is private):

```shell
docker login ghcr.io
IMAGE=ghcr.io/luca-blum/obsidian-knowledge-mcp:<commit-sha> \
  docker compose --file deploy/docker-compose.yml --env-file /srv/knowledgebase/.env up --detach --pull always --wait
```

## Add a Collection

Each Collection is one service with its own schema, database role, port, Vault and tokens. Everything else comes from
`x-collection` and `x-collection-environment` in `docker-compose.yml`. For a Collection `cooking`:

1. `postgres/collections.sql`: add `CALL pg_temp.isolate_collection('cooking', :'cooking_password');`. The role is
   named like the schema, and the instance connects as the role named like its `COLLECTION_SCHEMA`.
2. `docker-compose.yml`, service `postgres-init`: add `--set=cooking_password=${COOKING_DB_PASSWORD:?set in .env}`
   before the `--file` lines.
3. `docker-compose.yml`: copy the service `knowledgebase-training` to `knowledgebase-cooking`. Set `COLLECTION_NAME`
   and `COLLECTION_SCHEMA` to `cooking`, and replace every `TRAINING_` variable with `COOKING_`. Change nothing else
   there: a setting all Collections share belongs in the `x-collection` anchors. Check that the new instance still fits
   the [Memory](#memory) budget.
4. `.env.example` and `/srv/knowledgebase/.env`: add the `COOKING_` values. Give it its own `COOKING_MCP_PORT`, and
   generate `COOKING_DB_PASSWORD` and the tokens as in [Configuration](#1-configuration).
5. Sync the Vault to `COOKING_VAULT_DIR` as in [Syncthing](#3-syncthing-for-the-vault). Add the Vault to the importer's
   launchd agent (another `--vault` pair, see `importer/README.md`).
6. Calibrate its Minimum Similarity with the Vault's Evaluation Set in `_eval/`, then merge to deploy. The `.env` on
   the Pi must hold the new values first, or the deploy stops with `set in .env`.

## Memory

`<COLLECTION>_HEAP` (default `-Xmx512m`) caps each instance's Java heap, and `mem_limit: 900m` caps each Collection's
container. Two instances take at most 1.8 GB, which leaves room for Postgres, Ollama with `bge-m3` (about 1.2 GB
loaded) and the system in 8 GB. `docker stats --no-stream` shows what each container really uses.

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| The `deploy` job waits for a runner forever | The runner is offline: `sudo systemctl status 'actions.runner.*'`. |
| `set in .env` error in the `deploy` job | `/srv/knowledgebase/.env` is missing or a required value is empty. |
| `401` from `/mcp` | The client sends no token or one that is not in `.env`. Check the connector's `Authorization` header. |
| `No client token is configured` in the logs | Every `<COLLECTION>_TOKEN_<CLIENT>` of that Collection in `.env` is empty. |
| `knowledgebase-engineering` never gets healthy | Look at its logs. It waits for Postgres and Ollama first, and the first model download can take a while. |
| Search finds nothing | The Vault is empty on the Pi (check the Syncthing connection), or the files arrived after the service started (restart it). |
| `AccessDeniedException` in the logs | The Notes are not readable for the container: see *Ignore permissions* above. |
| `timeout period ... exceeded while executing POST /api/embed` | Ollama is slower than `OLLAMA_TIMEOUT` (default `120s`). Raise it in `.env`. |
