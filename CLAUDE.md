## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues on Luca-Blum/obsidian-knowledge-mcp (via `gh`). See `docs/agents/issue-tracker.md`.

### Triage labels

Uses the five default labels: needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Testing

Name test methods `when<Action>_given<Context>_then<Outcome>`, three parts separated by underscores, e.g. `whenIndexVault_givenNoFiles_thenReturnZero`. Setup methods (`@BeforeEach`) and ArchUnit rules are exempt.
