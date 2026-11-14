## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues on Luca-Blum/obsidian-knowledge-mcp (via `gh`). See `docs/agents/issue-tracker.md`.

### Triage labels

Uses the five default labels: needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Architecture

Hexagonal with jMolecules annotations; ADR `docs/adr/0003-ports-are-interfaces-and-roles-are-annotated.md` has the full rules and `ArchitectureTest` enforces them. Run tests with JDK 25 (`JAVA_HOME=$(/usr/libexec/java_home -v 25)`).

- Every type in `application` and `adapter` carries its role annotation, and every public concept in `domain` is a `@ValueObject` or `@Service`. Only utility classes, internal builders and package-private adapter helpers (such as `BearerTokenFilter`) stay unmarked. Names carry the concept, not the role: no `Port`, `Repository` or `Reader` suffixes.
- Ports are interfaces in `application`: `@PrimaryPort` for what the outside asks of the core, `@SecondaryPort` for what the core needs.
- A use case is a `@PrimaryPort` interface plus an `@Application` class named after how it works. Adapters depend on the port, never on the `@Application` class.
- Adapters are named after the technology (`PostgresIndex`, `OllamaEmbedding`). Never annotate a concrete class as a port to satisfy a rule; fix the design instead.

## Code style

Readability comes first: code should read like prose to someone new to the codebase.

- Write small functions that do one thing, named so the call site explains itself. Split a function when it needs a comment to explain its parts.
- Keep it simple: no abstractions, interfaces or configuration for needs that don't exist yet. Plain readable code beats clever code.
- Improve the package structure whenever you touch an area and it no longer fits, e.g. a package holds too many classes or mixes unrelated concepts. Group by concept, and keep the moves within the architecture rules above.

## Testing

Name test methods `when<Action>_given<Context>_then<Outcome>`, three parts separated by underscores, e.g. `whenIndexVault_givenNoFiles_thenReturnZero`. Setup methods (`@BeforeEach`) and ArchUnit rules are exempt.

## Wording

Call whatever uses the service the AI Agent (see `GLOSSARY.md`), never Claude: in code, comments, docs and tool descriptions. Name a concrete product such as Claude Code or claude.ai only where the text is about setting up that client.
