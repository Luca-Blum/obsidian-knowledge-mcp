# claude.ai reaches a Collection with its static Client token, not through OAuth

Status: Accepted · 2026-11-14

claude.ai reaches a Collection as a custom connector with Authentication "No sign in" and the request header `Authorization: Bearer <token>`, where the token is the Client's static bearer token from #13. The service checks it like Claude Code's. Custom connectors have offered request headers since August 2026, which a test against the Engineering Collection confirmed on 2026-11-14. The value needs the `Bearer ` prefix: a bare token gets `401`, and the connector reports only "Couldn't connect to the server".

## Considered Options

- **An OAuth authorization server inside every instance** (#52, PR #56). Built and tested, but about 1400 lines of security code to maintain, written for a premise that no longer holds: that custom connectors offer only OAuth. It also has to follow the MCP specification, which now prefers Client ID Metadata Documents to Dynamic Client Registration.
- **A proven authorization server in front of the service**, such as Ory Hydra, Pomerium or Keycloak, with `quarkus-oidc` validating the tokens. Brings no OAuth code of our own, but adds a component to every Collection and has its own rough edges with claude.ai. Kept as an idea in `docs/ideas.md` for the day a static token is no longer enough.

## Consequences

- Every Collection stays one service with no OAuth state, so it can be set up on its own, on the Pi or another server.
- A connector's authentication cannot be changed after it is created. Rotating claude.ai's token means setting a new one in `.env`, deploying, and creating the connector again.
- claude.ai stores the token encrypted and sends it on every request. There is no consent step, which is fine while every Client belongs to the same user.
- Without OAuth, nothing forces a Collection onto the bare root of its own origin. Giving each Collection its own public origin is decided in #55 for independence, not for OAuth.
