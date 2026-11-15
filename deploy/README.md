# Deploy to the Raspberry Pi

The knowledge service runs on a Raspberry Pi 5 (8 GB). Every push to `main` deploys it there automatically.

## What runs where

```
Mac                                    Raspberry Pi
───                                    ────────────
Obsidian + Vaults ──── Syncthing ────▶ /srv/vaults/Engineering, /srv/vaults/Training (read-only copies)
importer (launchd)                                         │ each mounted read-only into its own instance
Claude Code ── tailnet ─┬─▶ tailscale-engineering ─▶ knowledgebase-engineering ─┬─▶ postgres (pgvector), one schema each
claude.ai ──── Funnel ──┴─▶ tailscale-training ────▶ knowledgebase-training ────┴─▶ ollama (embedding model)
```

Every Collection has its own Tailscale node, a sidecar container, and with it its own public HTTPS origin, e.g.
`https://knowledge-engineering.<tailnet>.ts.net`. Claude Code and claude.ai both use that URL, each with its own token:
claude.ai comes in from the internet through Tailscale Funnel on port 443, and the Mac, which is in the tailnet, goes
through the tailnet. The instance shares the sidecar's network and publishes no port on the Pi, so all instances use
the same internal port 8081. A Collection needs nothing from another one, so it can move to another server on its own.

On the Pi everything runs in Docker, started from `docker-compose.yml`:

| Container | Image | Job |
| --- | --- | --- |
| `postgres` | `pgvector/pgvector:pg17` | Stores the index. Data in the Docker volume `knowledgebase_postgres`. |
| `postgres-init` | `pgvector/pgvector:pg17` | Gives each Collection its own database role that sees only its schema, then exits. |
| `ollama` | `ollama/ollama` | Computes embeddings. Models in the Docker volume `knowledgebase_ollama`. |
| `ollama-pull` | `ollama/ollama` | Downloads the embedding model (`bge-m3`) once, then exits. |
| `tailscale-engineering` | `tailscale/tailscale` | The Engineering Collection's Tailscale node `knowledge-engineering`. Funnel on 443 proxies to its instance. State in the Docker volume `knowledgebase_tailscale-engineering`. |
| `knowledgebase-engineering` | `ghcr.io/luca-blum/obsidian-knowledge-mcp` | The service for the Engineering Collection. Serves MCP on port 8081 inside the network of `tailscale-engineering`. |
| `tailscale-training` | `tailscale/tailscale` | The same for the Training Collection: node `knowledge-training`. |
| `knowledgebase-training` | `ghcr.io/luca-blum/obsidian-knowledge-mcp` | The service for the Training Collection. |

Both Collection services run the same image with the same settings (`x-collection` in `docker-compose.yml`), and
both sidecars too (`x-tailscale-sidecar`, with `tailscale/serve.json` for Funnel). They differ in their values (name,
schema, Vault, tokens, Tailscale auth key, Minimum Similarity and tool description) and in which sidecar each instance
belongs to.

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

## Security model

- **Every request needs a token.** Each Client has its own bearer token per Collection, set only in `.env`. Anything
  without a configured token gets `401`, except health (`/q/health`), and an instance without any token refuses to
  start. Locking out one Client means emptying its token.
- **Each Collection sees only its own data.** It connects as a database role that owns its schema and cannot read
  another's, and only its own Vault is mounted into its container.
- **The Vault is read-only.** It is mounted read-only, Syncthing on the Pi only receives, and `read_note` reads from
  the index by key, so no request reaches the file system.
- **The Pi opens no port.** The instances are reachable only through their Tailscale sidecars, and the deploy runner
  polls GitHub for jobs.

## First setup on the Pi

You need Raspberry Pi OS (64-bit) with Docker, Tailscale and the GitHub CLI (`gh`, logged in with `gh auth login`).
Your user must be in the `docker` group.

Raspberry Pi OS turns off the kernel's memory controller, and without it Docker ignores every `mem_limit`. Turn it on
before the first deploy, then reboot:

```shell
sudo sed -i '1 s/$/ cgroup_enable=memory/' /boot/firmware/cmdline.txt   # the file is a single line
sudo reboot
grep -o memory /sys/fs/cgroup/cgroup.controllers                        # prints "memory"
```

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
- `<COLLECTION>_TS_AUTHKEY` is set in the next step.

### 2. Tailscale: one public origin per Collection

Each Collection's sidecar logs in to your tailnet as its own node with the tag `tag:knowledge`. A tagged node belongs
to the tag instead of your user, so its key never expires, and Funnel is allowed for the tag in one place.

**Once per tailnet**, in the Tailscale admin console → Access controls, add the tag and allow it Funnel:

```jsonc
"tagOwners": {
  "tag:knowledge": ["autogroup:admin"]
},
"nodeAttrs": [
  { "target": ["tag:knowledge"], "attr": ["funnel"] }
]
```

If `nodeAttrs` already has a `funnel` entry, for example for `autogroup:member`, add `tag:knowledge` to its `target`
instead. HTTPS certificates must be on too (admin console → DNS → HTTPS Certificates).

claude.ai comes in from the internet through Funnel. A device that is itself in the tailnet, such as the Mac with
Claude Code, resolves the same name to the node's tailnet address and goes through the tailnet instead, so the access
rules apply. The default policy allows everything. If yours has its own rules, let your devices reach the tag on 443:

```jsonc
"grants": [
  { "src": ["autogroup:member"], "dst": ["tag:knowledge"], "ip": ["443"] }
]
```

**Once per Collection**, generate an auth key (admin console → Settings → Keys → Generate auth key):

- Tags: `tag:knowledge`.
- Reusable: off. Ephemeral: off, or the node disappears whenever its container stops.
- Pre-approved: on, if your tailnet requires device approval.

Put it into `.env` as `ENGINEERING_TS_AUTHKEY` (and `TRAINING_TS_AUTHKEY` for the Training Collection). The sidecar
uses the key only for its first login and keeps the node's identity in its volume, so the key may expire afterwards.
If you remove that volume, generate a new key, or the sidecar cannot log in again.

On every start the sidecar reads `tailscale/serve.json`, which turns on Funnel on 443 to the instance's port 8081.
Funnel therefore needs no manual `tailscale funnel` and survives a recreate of the container. After the first deploy
the Collection answers at `https://knowledge-engineering.<tailnet>.ts.net`, where `<tailnet>` is your tailnet's DNS name
(admin console → DNS). The first request can take a few seconds while the node gets its certificate.

### 3. GitHub Actions runner

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

### 4. Syncthing for the Vault

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

### 5. First deploy

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
docker compose --project-name knowledgebase ps --all                # every container "healthy", ollama-pull "exited (0)"
curl https://knowledge-engineering.<tailnet>.ts.net/q/health/ready  # Engineering: {"status":"UP", ...}
curl https://knowledge-training.<tailnet>.ts.net/q/health/ready     # Training
docker logs --tail 50 knowledgebase-knowledgebase-engineering-1
docker stats --no-stream                                            # memory per container, see Memory
```

## Connect Claude Code from the Mac

Each Collection is its own MCP server with its own token. The URL is the same as claude.ai's:

```shell
claude mcp add --transport http knowledge-engineering https://knowledge-engineering.<tailnet>.ts.net/mcp \
  --header "Authorization: Bearer <ENGINEERING_TOKEN_CLAUDE_CODE from .env>"
claude mcp add --transport http knowledge-training https://knowledge-training.<tailnet>.ts.net/mcp \
  --header "Authorization: Bearer <TRAINING_TOKEN_CLAUDE_CODE from .env>"
```

If a connector of that name already exists, remove it first with `claude mcp remove <name>`.

## Connect claude.ai

claude.ai sends its own static token in a request header (see ADR 0005). In claude.ai → Settings → Connectors → Add
custom connector, create one connector per Collection:

| Field | Value |
| --- | --- |
| Name | `knowledge-engineering` |
| URL | `https://knowledge-engineering.<tailnet>.ts.net/mcp` |
| Authentication | No sign in |
| Request header | Name `Authorization`, value `Bearer <ENGINEERING_TOKEN_CLAUDE_AI from .env>` |

The value needs the `Bearer ` prefix. A bare token gets `401`, and claude.ai shows only "Couldn't connect to the
server". Check the connector by asking claude.ai something the Collection knows: the answer cites Search Hits.

- **Lock out claude.ai:** empty `<COLLECTION>_TOKEN_CLAUDE_AI` in `.env` and deploy. Every request with the old token
  then gets `401`. Claude Code keeps working with its own token.
- **Rotate the token:** set a new `<COLLECTION>_TOKEN_CLAUDE_AI` (`openssl rand -hex 32`) and deploy. A connector's
  authentication cannot be changed after it is created, so remove the connector in claude.ai and create it again with
  the new token. Claude Code's token rotates the same way with `claude mcp remove` and `claude mcp add`.

## Day to day

| Task | How |
| --- | --- |
| Deploy a change | Merge to `main`. |
| Roll back | GitHub → Actions → Deploy → pick the run of an earlier commit → Re-run all jobs. |
| Change configuration | Edit `/srv/knowledgebase/.env`, then rerun the latest Deploy workflow, or run the command below. |
| Lock out a client | Empty its `<COLLECTION>_TOKEN_<CLIENT>` in `.env` and deploy. To rotate, see [Connect claude.ai](#connect-claudeai). |
| Force a Re-Index | `curl -X POST -H "Authorization: Bearer <token>" https://knowledge-<collection>.<tailnet>.ts.net/admin/re-index` |
| Deploy by hand | See below. |

To deploy by hand from a clone on the Pi (the `docker login` is only needed while the image is private):

```shell
docker login ghcr.io
IMAGE=ghcr.io/luca-blum/obsidian-knowledge-mcp:<commit-sha> \
  docker compose --file deploy/docker-compose.yml --env-file /srv/knowledgebase/.env up --detach --pull always --wait
```

## Add a Collection

Each Collection is one service with its own schema, database role, Tailscale node, Vault and tokens. Everything else
comes from the `x-collection` and `x-tailscale-sidecar` anchors in `docker-compose.yml`. For a Collection `cooking`:

1. `postgres/collections.sql`: add `CALL pg_temp.isolate_collection('cooking', :'cooking_password');`. The role is
   named like the schema, and the instance connects as the role named like its `COLLECTION_SCHEMA`.
2. `docker-compose.yml`, service `postgres-init`: add `--set=cooking_password=${COOKING_DB_PASSWORD:?set in .env}`
   before the `--file` lines.
3. `docker-compose.yml`: copy the services `tailscale-training` and `knowledgebase-training` to `tailscale-cooking`
   and `knowledgebase-cooking`, and add the volume `tailscale-cooking`. Replace every `training` with `cooking` (the
   node `knowledge-cooking`, the volume, `network_mode`, `depends_on`, `COLLECTION_NAME` and `COLLECTION_SCHEMA`) and
   every `TRAINING_` variable with `COOKING_`. Change nothing else there: a setting all Collections share belongs in the
   `x-collection` and `x-tailscale-sidecar` anchors. Check that the new instance still fits the [Memory](#memory)
   budget.
4. `.env.example` and `/srv/knowledgebase/.env`: add the `COOKING_` values. Generate `COOKING_DB_PASSWORD` and the
   tokens as in [Configuration](#1-configuration), and the auth key `COOKING_TS_AUTHKEY` as in
   [Tailscale](#2-tailscale-one-public-origin-per-collection).
5. Sync the Vault to `COOKING_VAULT_DIR` as in [Syncthing](#4-syncthing-for-the-vault). Add the Vault to the importer's
   launchd agent (another `--vault` pair, see `importer/README.md`).
6. Calibrate its Minimum Similarity with the Vault's Evaluation Set in `_eval/`, then merge to deploy. The `.env` on
   the Pi must hold the new values first, or the deploy stops with `set in .env`.
7. Connect Claude Code and claude.ai to `https://knowledge-cooking.<tailnet>.ts.net/mcp` as above.

## Memory

`<COLLECTION>_HEAP` (default `-Xmx512m`) caps each instance's Java heap, and `mem_limit: 900m` caps each Collection's
container. Two instances take at most 1.8 GB, which leaves room for Postgres, Ollama with `bge-m3` (about 1.2 GB
loaded) and the system in 8 GB. Each Tailscale sidecar takes a few dozen MB. `docker stats --no-stream` shows what each
container really uses; it shows each Collection's limit as `900MiB` only when the memory controller is on (see
[First setup](#first-setup-on-the-pi)).

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| The `deploy` job waits for a runner forever | The runner is offline: `sudo systemctl status 'actions.runner.*'`. |
| `set in .env` error in the `deploy` job | `/srv/knowledgebase/.env` is missing or a required value is empty. |
| `401` from `/mcp` | The client sends no token or one that is not in `.env`. Check the connector's `Authorization` header: its value is `Bearer <token>`, a bare token is refused. `docker logs` of the instance shows every refused request. |
| claude.ai: "Couldn't connect to the server" | First `docker logs --tail 50 knowledgebase-knowledgebase-<collection>-1`. A refused request there is a `401`, see above. Nothing there means the request never arrived: check the connector's URL and `curl https://knowledge-<collection>.<tailnet>.ts.net/q/health/ready` from outside the tailnet, e.g. a phone without Tailscale. |
| `https://knowledge-<collection>...` does not answer | From a device in the tailnet, check the access rules in [Tailscale](#2-tailscale-one-public-origin-per-collection). Otherwise: `docker logs knowledgebase-tailscale-<collection>-1`. `docker exec knowledgebase-tailscale-<collection>-1 tailscale funnel status` must show Funnel on 443 to `http://127.0.0.1:8081`. If it says Funnel is not allowed, the node lacks `tag:knowledge` or the policy lacks the `funnel` attribute. |
| An instance is up but cannot be reached, after its sidecar alone was recreated | The instance shares the sidecar's network, so a new sidecar container leaves it without one. Run `docker compose up` for both, as a deploy does, or restart the instance. |
| `tailscale-<collection>` never gets healthy | The node is not logged in: the auth key is missing, used up or expired, or the device waits for approval in the admin console. Generate a new key, put it into `.env` and deploy. |
| The node is called `knowledge-engineering-1` | A node of that name already existed. Remove the old one in the admin console and recreate the sidecar, or the URL changes. |
| `No client token is configured` in the logs | Every `<COLLECTION>_TOKEN_<CLIENT>` of that Collection in `.env` is empty. |
| `knowledgebase-engineering` never gets healthy | Look at its logs. It waits for Postgres and Ollama first, and the first model download can take a while. |
| Search finds nothing | The Vault is empty on the Pi (check the Syncthing connection), or the files arrived after the service started (restart it). |
| `AccessDeniedException` in the logs | The Notes are not readable for the container: see *Ignore permissions* above. |
| `timeout period ... exceeded while executing POST /api/embed` | Ollama is slower than `OLLAMA_TIMEOUT` (default `120s`). Raise it in `.env`. |
