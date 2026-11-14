# Each Collection is its own OAuth authorization server on its own public origin

claude.ai reaches a Collection as a custom connector, and custom connectors offer only OAuth: a static `Authorization` header cannot be set, so the static bearer token per Client from #13 cannot serve claude.ai. Each instance therefore issues OAuth tokens itself. It serves discovery (`/.well-known/oauth-authorization-server`, `/.well-known/oauth-protected-resource`), dynamic client registration, `/authorize` with a consent page guarded by a passphrase, and `/token`, all at its own origin root. Claude Code keeps its static bearer token.

Every Collection gets its own Tailscale sidecar node with Funnel on port 443, e.g. `knowledge-engineering.<tailnet>.ts.net`. Claude's OAuth client sends `/authorize` and `/token` to the bare origin root, whatever path the issuer has, so two authorization servers cannot share one origin by path. And Funnel offers 443, 8443 and 10000, but claude.ai's connector backend only got through on 443, so they cannot share one node by port. Both were found running garmin-mcp and notebooklm-mcp on the same Pi.

## Considered Options

- **One shared gateway for all Collections**, one origin with one authorization server and the MCP endpoints by path (`/engineering/mcp`). One node and one passphrase, but a separate deployable next to the instances, and a token is scoped to its Collection only if claude.ai sends the `resource` parameter. Rejected while there are few Collections, because each Collection is a copy of the same instance with its own configuration.
- **Keycloak or another external authorization server.** Brings no authorization server code, but costs memory on the Pi and would have to sit at each Collection's origin root anyway.

## Consequences

- OAuth state (clients, grants, tokens) lives in the Collection's own schema, so restarts and redeploys keep it and no token of one Collection is known to another.
- Access and refresh tokens are random strings, stored only as hashes, so revocation takes effect at once. An access token lives 1 hour. A refresh token lives 30 days and is replaced on every use.
- Changing the passphrase and restarting revokes every grant: the instance stores a hash of the passphrase and drops all grants when it differs at start-up. The passphrase must have at least 20 characters, so guessing it through the public consent page is hopeless without a rate limit.
- Without a passphrase OAuth is off: the endpoints do not exist, and only the static Client tokens are accepted, also when access tokens from an earlier time are still stored. So deploying the code changes nothing until a Collection sets a passphrase and its public URL.
- Claude Code uses the same public URL as claude.ai, with its static token; the instance publishes no port on the host.
- A new Collection needs its own Tailscale auth key and passphrase.
