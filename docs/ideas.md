# Ideas

Possibilities that are not tickets yet. Each entry says why it waits and what would make it worth another look. When an idea becomes a ticket, link the issue here and move the entry out. When it becomes a decision, record it as an ADR in `docs/adr/`.

## Describe pictures with a larger model

**Idea:** Docling can replace the `<!-- image -->` placeholder of a picture with a generated description (`do_picture_description`). Pointing it at a larger local vision model through `PictureDescriptionApiOptions` (LM Studio or Ollama) might read the labels in diagrams.

**Why it waits:** An experiment with Docling's default model, SmolVLM-256M, on the clean-architecture diagram of Clean Architecture (PDF pages 229-230) produced only "In this image, we can see a diagram. In the diagram, we can see the labels of different things." and leaked the model's `<end_of_utterance>` token into the Note. The surrounding text of the book already explains most diagrams, and a wrong description would stand in a Source Note like book text. A larger model has not been tried yet.

**Look again when:** The Evaluation Set (#4) contains questions that only a diagram can answer, or a source depends on charts or tables that exist only as pictures. Try a larger model on those sources only, and mark any description clearly as generated.

## Page number in the picture placeholder

**Idea:** Replace the bare `<!-- image -->` with a visible hint that names the PDF page, for example `> [Figure on page 229 of the source PDF]`. Docling records the page of every picture, so the importer could read it per picture. Consecutive pictures collapse into one hint. Formats without pages (EPUB, DOCX) keep a hint without a page.

**Why it waits:** The AI Agent can already tell from the placeholder that a picture was there, and the PDF behind the Note's `source` link holds the picture for a human. There is no case yet where the missing page number hurt.

**Look again when:** Reading a Chunk regularly leaves a question about a picture, or the idea below gets built, because it needs the page. Note that the page is the PDF's page, not the number printed in the book.

## MCP tool that returns a PDF page

**Idea:** A tool in the knowledge service that returns one page of the PDF behind a Source Note, so the AI Agent can look at a picture itself. It follows the Note's `source` link and needs the PDF on the Pi, which Syncthing already carries there.

**Why it waits:** It depends on the page number in the placeholder (previous idea), and there is no evidence yet that the AI Agent needs pictures to answer from the Vaults. It also touches the knowledge service's API and its read-only handling of Vaults.

**Look again when:** The page number in the placeholder exists and the Evaluation Set or everyday use shows answers that fail for lack of a picture.

## OAuth through a proven authorization server

**Idea:** Let claude.ai sign in through OAuth instead of a static token (ADR 0005). Quarkus brings no authorization server: `quarkus-oidc` only validates tokens, by introspection or by JWT signature, and can publish the protected resource metadata. The authorization server would be a proven component next to each Collection:

- **Ory Hydra:** OpenID Certified, small Go binary, stores its state in Postgres. It does PKCE, refresh rotation and Dynamic Client Registration; we would keep only a login and consent app, such as a passphrase page. Its open-source edition will not get Client ID Metadata Documents (ory/hydra#4061, Enterprise only), its endpoints live under `/oauth2/…`, and one report saw claude.ai reject its registration response because of empty `*_uri` fields.
- **Pomerium:** an identity-aware proxy with MCP support that already accepts Client ID Metadata Documents. It needs an upstream identity provider.
- **Keycloak:** the standard, but about 1-1.25 GB of RAM even when idle, too much for the Pi.
- Not robust enough at the time of writing: `tsidp` (Tailscale, experimental, below 1.0) and `mcp-auth-proxy` (a one-person project).

What we learned about claude.ai: the connector recommends "Use Claude's published identity" (most likely Client ID Metadata Documents) over "Register automatically" and a client of our own, whose redirect URL is `https://claude.ai/api/mcp/auth_callback`. It honours the endpoints from the authorization server metadata, and falls back to `/authorize`, `/token` and `/register` on the MCP server's origin only when discovery fails, for example with a path in the issuer (anthropics/claude-ai-mcp#82). The connector backend reaches only port 443. PR #56 (closed, branch `issue-52-oauth`) holds a complete authorization server inside the service with tests and can serve as a reference.

**Why it waits:** The static Client token works with claude.ai, needs no extra component and keeps every Collection standalone. Every option above adds a service per Collection and its own quirks with claude.ai.

**Look again when:** claude.ai drops request headers, a Collection is shared with other people who need their own sign-in and revocation, or tokens should expire without creating the connector again.
