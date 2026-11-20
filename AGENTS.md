# AGENTS.md

Guidance for AI coding agents (Claude Code, Codex, Cursor, etc.) working in this repository.
Read this file first, then the docs it points to for the area you are touching.

## What we are building

**OneMind** is a mobile back-office app for bar and restaurant owners.
It reads data from the venue's POS (Storyous, read-only) and turns it into:

- live figures for tonight (revenue, open tabs, occupancy, staff on shift, possible loss),
- sales analysis (by period, product, hour, payment type, discounts and losses),
- staff and table performance,
- purchasing and stock (order plan, counts, transfers, invoice scanning, recipes),
- an AI assistant that answers questions from POS data and the owner's documents, with sources.

Full product brief: [docs/product/OVERVIEW.md](docs/product/OVERVIEW.md).
**What is in the first release:** [docs/product/MVP_SCOPE.md](docs/product/MVP_SCOPE.md). Don't build
out-of-scope features unless asked.

## Repository layout (monorepo)

```
/
├── AGENTS.md              ← you are here (shared rules)
├── CLAUDE.md              ← imports AGENTS.md for Claude Code
├── docs/                  ← single source of truth for product, domain, API, design
│   ├── product/           ← overview, screens, navigation
│   ├── domain/            ← glossary, model, metrics, stock logic
│   ├── integrations/      ← Storyous POS
│   ├── ai/                ← assistant, suggestions, knowledge, invoice OCR
│   ├── design/            ← design system (tokens, components)
│   ├── api/               ← API conventions and endpoint inventory
│   ├── architecture/      ← architecture notes + ADRs
│   └── OPEN_QUESTIONS.md  ← unresolved decisions (check before assuming)
├── backend/               ← Kotlin + Spring Boot API  → see backend/AGENTS.md
├── mobile/                ← Kotlin Multiplatform app (Android + iOS) → see mobile/AGENTS.md
└── infra/                 ← docker compose (local + prod), Caddy, deploy/backup/restore scripts (ADR 0008)
```

`backend/`, `mobile/` and `infra/` do not exist yet: the project is starting from scratch.
When you create them, follow the sub-project AGENTS.md files.

## Where to look

| You are working on…                      | Read                                                                 |
|------------------------------------------|----------------------------------------------------------------------|
| Any feature                              | [docs/product/SCREENS.md](docs/product/SCREENS.md), [docs/domain/GLOSSARY.md](docs/domain/GLOSSARY.md) |
| A number shown on screen                 | [docs/domain/METRICS.md](docs/domain/METRICS.md) (exact definitions) |
| Entities, DB schema, DTOs                | [docs/domain/MODEL.md](docs/domain/MODEL.md)                         |
| Stock, orders, counts, recipes           | [docs/domain/STOCK.md](docs/domain/STOCK.md)                         |
| POS sync                                 | [docs/integrations/STORYOUS.md](docs/integrations/STORYOUS.md)       |
| Storyous partnership, competitors        | [docs/product/STORYOUS_PARTNERSHIP.md](docs/product/STORYOUS_PARTNERSHIP.md) |
| Assistant, suggestions, documents, OCR   | [docs/ai/ASSISTANT.md](docs/ai/ASSISTANT.md)                         |
| UI, colors, type, components             | [docs/design/DESIGN_SYSTEM.md](docs/design/DESIGN_SYSTEM.md)         |
| Navigation between screens               | [docs/product/NAVIGATION.md](docs/product/NAVIGATION.md)             |
| Endpoints, request/response shapes       | [docs/api/README.md](docs/api/README.md)                             |
| Architecture decisions                   | [docs/architecture/README.md](docs/architecture/README.md)           |

The visual source of truth is the design canvas
(https://claude.ai/artifact/1n4dWyBqXD1AzPVNi8bTz3, page **Cross-platform**).
The **Screens** page is an earlier iOS-styled direction: use it only for behaviour that the
Cross-platform page does not show (for example the Insights suggestion cards).

## Product rules that must never be broken

1. **The POS is read-only.** We never write to Storyous. No endpoint, job or client may call a
   mutating Storyous API.
2. **No invented numbers.** Every figure shown to the user comes from POS data, user input or
   a documented calculation in `docs/domain/METRICS.md` / `STOCK.md`. If data is missing, show
   an empty state, never a placeholder value.
3. **The assistant always names its sources** (POS data and/or document names). Staff-role users
   only get answers from documents marked *All staff*.
4. **Nothing is deleted from stock history.** Corrections are new records with a reason and an
   author. Transfers move stock between locations and never change the venue total.
5. **Money is exact.** Store money as integer minor units (haléř for CZK) or `BigDecimal` with a
   currency code, never `Float`/`Double`.
6. **Bar days cross midnight.** "Today" means the current *business day* of the venue
   (e.g. 16:00 to 03:00), in the venue's time zone. See `docs/domain/METRICS.md#business-day`.
7. **Multi-venue from day one.** Every query is scoped by venue; "All venues" is an aggregate view.

## Working agreements for agents

- **Check `docs/OPEN_QUESTIONS.md` before making an assumption.** If your task depends on an open
  question, ask the user instead of guessing, or implement behind a clearly named seam and note it.
- **Docs are part of the change.** If you change behaviour, a metric, an endpoint or a domain
  term, update the matching doc in the same change.
- **Use the glossary terms** in code, UI copy and API names (`openTab`, `possibleLoss`,
  `parLevel`, `pullList`, …). Do not invent synonyms.
- **API changes start in `docs/api/openapi.yaml`.** It is the only contract between backend and
  mobile; both generate code from it and there is no shared Kotlin module
  ([ADR 0001](docs/architecture/adr/0001_OPENAPI_CONTRACT_FIRST.md)). Changes in `/api/v1` are
  additive only; update spec, backend and mobile in the same PR.
- **Data access is jOOQ, never JPA**
  ([ADR 0002](docs/architecture/adr/0002_POSTGRES_FLYWAY_JOOQ.md)).
- **Read the ADRs** in `docs/architecture/adr/` before changing anything they cover.
- **Small, reviewable changes.** One feature or fix per PR. Include tests.
- **Never commit secrets** (Storyous credentials, LLM API keys, signing keys). Use env vars and
  local, git-ignored config.
- **Ask before** adding a new third-party dependency or service, changing the database schema in a
  non-additive way, or touching auth.

## Commit and PR conventions

- Conventional Commits: `feat(mobile): …`, `fix(backend): …`, `docs: …`, `chore: …`.
- Scope is `backend`, `mobile`, `docs` or `repo`.
- PR description: what changed, why, screenshots for UI, and which docs were updated.

## Status

Architecture is **not final**. `docs/architecture/README.md` lists proposed defaults and the
decisions still to be discussed. Treat anything marked *Proposed* as a default you may follow,
not a settled rule.
