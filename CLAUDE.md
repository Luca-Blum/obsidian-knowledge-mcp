## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues on Luca-Blum/obsidian-knowledge-mcp (via `gh`). See `docs/agents/issue-tracker.md`.

### Triage labels

Uses the five default labels: needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Architecture

Hexagonal with jMolecules annotations; ADR `docs/adr/0003-ports-are-interfaces-and-roles-are-annotated.md` has the full rules and `ArchitectureTest` enforces them. Run tests with JDK 25 (`JAVA_HOME=$(/usr/libexec/java_home -v 25)`).

- Every type in `domain`, `application` and `adapter` carries its role annotation. Names carry the concept, not the role: no `Port`, `Repository` or `Reader` suffixes.
- Ports are interfaces in `application`: `@PrimaryPort` for what the outside asks of the core, `@SecondaryPort` for what the core needs.
- A use case is a `@PrimaryPort` interface plus an `@Application` class named after how it works. Adapters depend on the port, never on the `@Application` class.
- Adapters are named after the technology (`PostgresIndex`, `OllamaEmbedding`). Never annotate a concrete class as a port to satisfy a rule; fix the design instead.

## Testing

Name test methods `when<Action>_given<Context>_then<Outcome>`, three parts separated by underscores, e.g. `whenIndexVault_givenNoFiles_thenReturnZero`. Setup methods (`@BeforeEach`) and ArchUnit rules are exempt.
