# Deploy to the Raspberry Pi

The knowledge service runs on a Raspberry Pi 5 (8 GB). Every push to `main` deploys it there automatically.

## What runs where

```
Mac                                    Raspberry Pi
───                                    ────────────
Obsidian + Vault  ──── Syncthing ────▶ /srv/vaults/Engineering (read-only copy)
importer (launchd)                              │ mounted read-only
Claude Code  ──── Tailscale, port 8081 ───▶ knowledgebase-engineering ──▶ postgres (pgvector)
                                                                     └──▶ ollama (embedding model)
```

On the Pi everything runs in Docker, started from `docker-compose.yml`:

| Container | Image | Job |
| --- | --- | --- |
| `postgres` | `pgvector/pgvector:pg17` | Stores the index. Data in the Docker volume `knowledgebase_postgres`. |
| `ollama` | `ollama/ollama` | Computes embeddings. Models in the Docker volume `knowledgebase_ollama`. |
| `ollama-pull` | `ollama/ollama` | Downloads the embedding model (`bge-m3`) once, then exits. |
| `knowledgebase-engineering` | `ghcr.io/luca-blum/obsidian-knowledge-mcp` | The service for the Engineering Collection. Serves MCP on port 8081. |

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
sudo mkdir -p /srv/knowledgebase /srv/vaults/Engineering
sudo chown -R $USER: /srv/knowledgebase /srv/vaults
cp deploy/.env.example /srv/knowledgebase/.env
sed -i "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$(openssl rand -hex 24)/" /srv/knowledgebase/.env
chmod 600 /srv/knowledgebase/.env
```

`.env.example` explains each value. Only `POSTGRES_PASSWORD` must be changed. The password is used only between
containers on the Pi, so you never type it anywhere. Keep the file: if you lose it, the Postgres volume can no longer
be opened with a new password.

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
2. Share the Vault folder with the Pi (folder → Edit → Sharing → tick the Pi). Note the folder's **Folder ID**
   (folder → Edit → General). For the Engineering Vault it is `Engineering`.
3. Copy the Mac's Device ID (Actions → Show ID).

Back on the Pi, add the Mac and the folder. `<FOLDER-ID>` must be exactly the Folder ID from the Mac, or Syncthing
treats them as two different folders and nothing syncs:

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
  cannot read them. If files arrived before the setting was on, fix them once with `chmod -R a+rX /srv/vaults/Engineering`.

`syncthing cli show pending folders` lists a folder the Mac offers that the Pi has not added, and shows its Folder ID.
The Pi's web UI only listens on the Pi itself. To open it from the Mac: `ssh -L 8384:localhost:8384 <user>@<pi>`, then
http://localhost:8384.

Check the sync with `syncthing cli show connections` (the Mac shows `"connected": true`) and `ls /srv/vaults/Engineering`.

### 4. First deploy

Push to `main`, or rerun the latest Deploy workflow (GitHub → Actions → Deploy → Re-run all jobs). The first run takes
a few minutes longer, because `ollama-pull` downloads the embedding model (about 1.2 GB).

## Indexing

For now the service indexes the whole Vault **once, when it starts**. It does not notice later changes yet
(Reconciliation, issue #11). It embeds Notes one chunk at a time on the Pi's CPU, which takes several seconds per
chunk: roughly an hour per 4 MB of Markdown. Every deploy restarts the service, so it indexes everything again. Search
works during indexing, but only finds the Notes indexed so far.

To pick up new Notes now, restart the service: `docker restart knowledgebase-knowledgebase-engineering-1`.

Progress, as the number of chunks in the index:

```shell
docker exec knowledgebase-postgres-1 psql -U knowledge -d knowledge -Atc "select count(*) from engineering.chunk"
```

## Check that it runs

```shell
docker compose --project-name knowledgebase ps --all           # every container "healthy", ollama-pull "exited (0)"
curl http://<pi-tailscale-name>:8081/q/health/ready            # {"status":"UP", ...}
docker logs --tail 50 knowledgebase-knowledgebase-engineering-1
```

## Connect Claude Code from the Mac

The Mac and the Pi must be in the same Tailscale network.

```shell
claude mcp add --transport http knowledge-engineering http://<pi-tailscale-name>:8081/mcp
```

## Day to day

| Task | How |
| --- | --- |
| Deploy a change | Merge to `main`. |
| Roll back | GitHub → Actions → Deploy → pick the run of an earlier commit → Re-run all jobs. |
| Change configuration | Edit `/srv/knowledgebase/.env`, then rerun the latest Deploy workflow, or run the command below. |
| Deploy by hand | See below. |

To deploy by hand from a clone on the Pi (the `docker login` is only needed while the image is private):

```shell
docker login ghcr.io
IMAGE=ghcr.io/luca-blum/obsidian-knowledge-mcp:<commit-sha> \
  docker compose --file deploy/docker-compose.yml --env-file /srv/knowledgebase/.env up --detach --pull always --wait
```

## Memory

`ENGINEERING_HEAP` (default `-Xmx512m`) caps the Java heap, and `mem_limit: 900m` caps the whole container. That
leaves room for Postgres and Ollama in 8 GB.

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| The `deploy` job waits for a runner forever | The runner is offline: `sudo systemctl status 'actions.runner.*'`. |
| `set in .env` error in the `deploy` job | `/srv/knowledgebase/.env` is missing or a required value is empty. |
| `knowledgebase-engineering` never gets healthy | Look at its logs. It waits for Postgres and Ollama first, and the first model download can take a while. |
| Search finds nothing | The Vault is empty on the Pi (check the Syncthing connection), or the files arrived after the service started (restart it). |
| `AccessDeniedException` in the logs | The Notes are not readable for the container: see *Ignore permissions* above. |
| `timeout period ... exceeded while executing POST /api/embed` | Ollama is slower than `OLLAMA_TIMEOUT` (default `120s`). Raise it in `.env`. |
