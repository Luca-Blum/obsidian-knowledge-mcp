# How This Project Is Built

This project was built agentically: AI Agents wrote much of the code and documentation. It was not vibe coding. I
set the architecture, made every decision, and reviewed every change in its pull request before it was merged. This
page shows the frame that makes that work.

## Roles

| Who | Does |
| --- | --- |
| Me | Decides what to build and how it should be structured, writes and triages issues, challenges plans, reviews every pull request, merges |
| AI Agent | Explores the code, proposes plans, writes code, tests and docs within the written rules, opens the pull request |
| CI, ArchUnit, Evaluation Set | Check on every change what a reviewer can overlook |

## A structured workflow instead of free-form prompts

The work runs on skills from [mattpocock/skills](https://github.com/mattpocock/skills), each a written procedure an
AI Agent follows for one kind of task:

- **Grilling** a plan before any code is written: the agent questions the idea until open points are decided.
- **Domain modeling**: terms that come up are settled in [`GLOSSARY.md`](../GLOSSARY.md), decisions with their
  trade-offs are recorded as [ADRs](adr/README.md).
- **Test-driven development**: red, green, refactor, with integration tests against real Postgres.
- **Code review** of a branch against two axes: the repository's standards and what the issue asked for.
- **Pull request descriptions** that explain the change for the reviewer.

## What every AI Agent reads first

The agent starts every task with the same context, kept in the repository and versioned with the code:

- [`CLAUDE.md`](../CLAUDE.md): the architecture rules (hexagonal, jMolecules roles, ports as interfaces), the code
  style (small functions that read like prose, no abstractions for needs that don't exist yet), the test naming
  convention `when<Action>_given<Context>_then<Outcome>`, and the wording rules.
- [`GLOSSARY.md`](../GLOSSARY.md): the domain language. Code, tests, docs and MCP tool descriptions use its terms,
  and each entry lists the synonyms to avoid.
- [`docs/adr/`](adr/README.md): the decisions already made, so an agent does not reopen them silently.
- [`docs/agents/`](agents/): where issues live, what the triage labels mean and how to use the domain docs.

## From issue to merge

1. **Issue.** Each piece of work is a GitHub issue. Triage labels (`needs-triage`, `ready-for-agent`,
   `ready-for-human`, …) say whether it is specified well enough for an agent.
2. **Plan.** For anything non-trivial the agent explores the code and proposes a plan, which I grill or correct
   before it starts.
3. **Branch and pull request.** One issue, one branch, one pull request. 33 pull requests have been merged into
   `main` so far; only the initial setup was committed directly.
4. **Checks.** CI runs the service tests, including `ArchitectureTest`, and the importer tests on every pull
   request. Search changes are measured with the [Evaluation Set](evaluation/README.md) before they are merged.
5. **Review.** I review the diff in the pull request, ask for changes, and merge. A merge to `main` deploys to the
   Raspberry Pi.

## Guard rails that do not depend on the reviewer

- **ArchUnit** fails the build when a dependency points the wrong way or a type lacks its role.
- **Integration tests** run against real Postgres with pgvector; a deterministic fake replaces only the embedding
  model.
- **The Evaluation Set** turns "search feels better" into Hit@5, Recall@5 and MRR, so a change that makes search
  worse is visible in numbers.
- **The deploy** fails when an instance is not healthy within ten minutes, so a broken image never stays live
  unnoticed.

## Examples where the human decision mattered

- **OAuth built, then dropped.** An authorization server for claude.ai was built and tested inside the service
  (#52, PR #56, about 1400 lines of security code). When claude.ai's custom connectors began to accept a static
  request header, I tested that against a live Collection and closed the pull request in favour of a static token
  per Client: much less code to secure and no extra component. The reasoning is in
  [ADR 0005](adr/0005-claude-ai-uses-a-static-token-not-oauth.md), the option kept for later in
  [`ideas.md`](ideas.md).
- **A rule made green the wrong way.** An early version satisfied the jMolecules architecture rule by marking the
  concrete use case classes as ports. That merged each port with its implementation and hid the boundary the rule
  protects. It was reversed in [ADR 0003](adr/0003-ports-are-interfaces-and-roles-are-annotated.md), and
  `CLAUDE.md` now says it outright: never annotate a concrete class as a port to satisfy a rule, fix the design.
- **Measured before merged.** Hybrid search at equal weight made results *worse* when the full-text search got the
  whole question. Instead of tuning weights until the numbers looked right, the `search` tool now asks the AI Agent
  for exact terms as `keywords`, and with them Hit@5 went from 0.88 to 1.00
  ([results](evaluation/README.md#results)).
