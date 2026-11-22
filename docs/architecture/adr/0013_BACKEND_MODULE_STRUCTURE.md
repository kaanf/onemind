# 0013 · Backend module internal structure

- **Status:** Accepted
- **Date:** 2026-11-21 (accepted 2026-11-21)
- **Deciders:** Kaan
- **Supersedes:** [0009](0009_BACKEND_MODULAR_MONOLITH.md)

## Context

ADR 0009 set up the modular monolith: business modules with their own PostgreSQL schemas, Spring
Modulith boundary checks and a "pragmatic hexagonal" layout. After the first slices (auth, idempotency)
three weaknesses showed up before the large POS, reporting and stock work starts:

- **`infra/` mixes two things:** jOOQ repositories and external clients (Apple/Google key fetching)
  sit in one package, so a rule like "jOOQ only in persistence code" can't be checked.
- **Use cases grow into service classes:** `SessionTokenService` already holds sign-in sessions,
  refresh and logout. With dashboards and stock this turns into large classes with many
  dependencies.
- **The module graph is implicit:** any module may call any other module's `api`, and `reporting`
  reads `pos` tables directly (ADR 0009 §1). Reports then depend on another module's table layout,
  which no code-level check sees and every `pos` migration can break.

This ADR keeps everything in ADR 0009 that still holds (modules, schemas, events, jobs, errors,
transactions) and adds a fixed internal structure, nine rules (R1–R9) and an explicit module graph.
Code is moved to it in a separate step (C2) after a reviewer audit, and the rules become build gates
in C3.

## Decision

### 1. Modules and schemas (unchanged from ADR 0009)

| Module | Owns | PostgreSQL schema |
|--------|------|-------------------|
| `account` | users, auth, organizations, venues, memberships, followed venues | `account` |
| `pos` | Storyous client, sync jobs, POS read model (bills, items, payments, discounts, deleted items, products, categories, tables, staff, shifts, Happy Hour rules) | `pos` |
| `reporting` | metric calculations, hourly rollup tables, screen queries | `reporting` |
| `stock` | stock items, locations, movements, receipts, counts, transfers, recipes, supplier aliases | `stock` |
| `common` | shared kernel: `Money`, `Quantity`, IDs, business-day calendar, errors, security, idempotency, events, pagination | `platform` (idempotency, event publication, scheduler) |

- One Gradle module; modules are top-level packages under `com.onemind.backend`, verified by Spring
  Modulith (2.2.0). The boundary test is mandatory in CI.
- Cross-module **foreign keys** stay allowed only to `account.venue` and `account.app_user`
  (database integrity, not code access). History and audit tables use `on delete set null` towards
  `app_user` (`DB_SCHEMA.md`).
- **Changed:** `reporting` no longer reads `pos` tables (R1, §4).

### 2. Packages inside a module

Package names stay as they are; `infra` is split in two.

```
<module>/
├── api/                 ← public facade: the only synchronous entry for other modules
│   └── events/          ← events this module publishes
├── web/                 ← @RestController implementing generated OpenAPI interfaces; mapping only
├── application/         ← one use-case class per screen or job (GetTodaySummary, SyncBills, EmailSignIn)
├── domain/              ← pure Kotlin: rules and calculations (BusinessDay, METRICS formulas, stock aggregates)
└── infra/
    ├── persistence/     ← jOOQ repositories and queries, returning domain types or plain data classes
    └── client/          ← adapters to external systems (IdTokenVerifier, Storyous client, SMTP)
```

**Dependency direction:** `web → application → domain ← infra`, with `api` as a second entry next
to `web`. Allowed dependencies inside a module, and nothing else:

| Package | May depend on (same module) |
|---------|-----------------------------|
| `web` | `application`, `domain` |
| `api` (facades and their DTOs) | `application`, `domain` |
| `api/events` | – (events carry only Kotlin and `common` types) |
| `application` | `domain`, `infra/persistence`, `infra/client`, `api/events` (to publish) |
| `infra/persistence`, `infra/client` | `domain` |
| `domain` | – |

- **No cycle with `api`:** facade classes in `api` call `application`; `application` never imports
  `api` except `api/events`. A type both sides need (a role, a result) is defined in `domain` or
  `application` and the facade maps it to its own DTO. Exceptions thrown by use cases live in
  `application` or `domain`; other modules don't catch them, `ApiExceptionHandler` maps them.
- `application` uses `infra/persistence` and `infra/client` classes **directly**. Under R9 there
  are no repository interfaces in `domain`, so `infra` doesn't implement domain ports. Repositories
  take and return domain types, plain Kotlin values or plain data classes declared next to them in
  `infra/persistence` (like today's `RefreshTokenRow`), never `application` types.
- `domain` depends on nothing in the module and only on the plain-Kotlin parts of `common` (`Money`,
  `Quantity`, IDs, `DomainException`).
- Other modules see only `api` and `api/events` (named interfaces), never `application`, `domain`
  or `infra` classes.
- `web` calls `application` use cases and may use `domain` types (a calculated figure) and `common` value types (`Money`) to
  map them to and from generated DTOs (R5), so use cases don't need extra application-level copies
  of domain results. It never touches `infra` or `api/events`.
- Wiring that belongs to a use case lives next to it in `application`: db-scheduler task beans (the
  body is a plain use-case method) and `@ApplicationModuleListener` methods (each is idempotent,
  ADR 0009 §5 still applies).
- **`common`** is organised by area (`email`, `error`, `idempotency`, `money`, `security`, `web`, …),
  not by layer, so the direction table doesn't apply to it. R3–R7 do: jOOQ code sits in
  `common/<area>/infra/persistence` (also the jOOQ `CurrencyConverter`), generated OpenAPI types only
  in `common/web`, transactions and job beans at the area's root (`common/events/EventPublicationJobs`,
  `common/idempotency/IdempotencyJobs`), since an area is its own small use case.

### 3. Rules

| ID | Rule |
|----|------|
| **R1** | A module touches only its own schema's generated jOOQ classes (`com.onemind.db.<own schema>`; `common` owns `platform`). Data of another module comes through that module's `api` or its events. No cross-schema joins in code. |
| **R2** | Use-case classes, not growing `*Service` classes. A use case covers one screen or job. A Spring bean in `application` with more than **5 collaborators** in its constructor must be split. `Clock` and `TransactionTemplate` don't count (a fixed allowlist in the gate); data classes, results and exceptions in `application` aren't use cases. |
| **R3** | `DSLContext` and other jOOQ types (`org.jooq..`, `com.onemind.db..`) are used only in `infra/persistence`. |
| **R4** | jOOQ `Record` types never leave `infra/persistence`: repositories return domain types, plain Kotlin values or plain data classes declared in `infra/persistence`. |
| **R5** | Generated OpenAPI models and interfaces (`com.onemind.api.v1..`) never leave `web` (a module's `web` or `common/web`). |
| **R6** | `domain` imports no Spring, jOOQ or Jackson. |
| **R7** | Transactions (`@Transactional`, `TransactionTemplate`) only in `application`. One per use case; no Claude or Storyous call inside one. |
| **R8** | The module graph is explicit: every module declares `@ApplicationModule(allowedDependencies = …)` (§4). Changing it changes this ADR. |
| **R9** | No interface without a second implementation. **Exceptions:** `PosProvider` (a mock/fixture implementation now, the real Storyous client later, other POS systems after that), `ReceiptExtractor` and `LlmClient` (tests need a fake that makes no network calls). `EmailSender` and `FileStorage` are concrete classes; switching provider is configuration. Test doubles alone don't count as a second implementation. Sealed interfaces for result types (`IdempotencyClaim`) are not ports and are fine. |

### 4. Module graph (R8)

| Module | Type | Allowed dependencies | Why |
|--------|------|----------------------|-----|
| `common` | open | – | Shared kernel; every module may use every `common` package. `common` itself uses no business module. |
| `account` | base | – | Users, venues and memberships. The others depend on it; it depends on none of them. |
| `pos` | – | `account::api`, `account::events` | Registers the venues it discovers and checks venue access for sync status; deletes its data on `OrganizationPurging`. Uses no other business module. |
| `reporting` | – | `account::api`, `account::events`, `pos::api`, `pos::events`, `stock::api`, `stock::events` | Venue access; deletes its rollups on `OrganizationPurging`; reads POS facts through the pos read API; refreshes rollups on `BillsSynced`; `possibleLoss` includes `stockCorrections` (METRICS), read through `stock::api` and refreshed on stock correction events. Without stock in the first release (P13) that part is an empty state, never 0. |
| `stock` | – | `account::api`, `account::events`, `pos::api`, `pos::events` | Venue access; deletes its data on `OrganizationPurging`; sold items and the product catalog for usage and recipes; deducts usage on `BillsSynced`. |

Every module also lists `common` in `allowedDependencies`: it is open, but an explicit list replaces
Spring Modulith's default, so it must be named. `api` and `api/events` are named interfaces (`::api`,
`::events`) in every module. Nothing depends
on `reporting`, and only `reporting` depends on `stock`, so the graph has no cycle (Spring Modulith
checks that). `stock` must never need `reporting`: a number both show is computed in `stock` and
read by `reporting`.

**Why more than `pos::events` (owner decision, 2026-11-21):** both also need venue
access (`account::api`, AGENTS.md rule 7), `reporting` needs stock corrections for `possibleLoss`,
and both need POS facts that an event can't carry reasonably (bill lines for a business day are thousands of rows, and the event payload is stored in
`platform.event_publication`). So they read through the pos read API below. Events stay small
signals (`BillsSynced(venueId, businessDays)`).

### 5. POS read API (replaces reporting's table access)

`pos/api` gets a read facade, owned and versioned by `pos`, that returns DTOs, never jOOQ types.
It returns **pages, never a lazy `Sequence` or stream**: a lazy result would keep a cursor and its
connection open across the module boundary, outside `pos`'s control. Each call is complete when it
returns; the caller asks for the next page with the keyset cursor it got.

- `PosReadModel.billLines(venueId, businessDays, after: BillLineCursor?, limit): Page<BillLineFact>`:
  paid bill lines (bill, business day, paid at, desk, staff, product, quantity, gross/net minor units,
  discount, refunded/deleted flags), ordered by bill and line.
- `payments(…)` and `deletedItems(…)`, paged the same way.
- `openTabs(venueId)` for the "now" figures of Today (ADR 0005), so `reporting` never reads `pos`
  tables, not even for live data.

Start with these four; products, desks and staff are added when a screen needs them.

`reporting` builds its hourly rollups in its own schema from these facts, inside the
`BillsSynced` listener. A rebuild job replays the same path per business day. The facade is where
`pos` keeps its table layout private: a `pos` migration changes the facade's implementation, not
reporting's queries.

Cost: a rollup refresh becomes "read from pos, write to reporting" instead of one
`insert … select` across schemas. At bar scale (hundreds to a few thousand bill lines per business
day and venue) that is milliseconds to seconds per refresh, outside request paths. If a measured
refresh ever gets too slow, `pos` can add an aggregated method to its facade (for example lines
already grouped by hour and product). The SQL stays inside `pos`.

### 6. Unchanged from ADR 0009

- **Events:** Spring Modulith JDBC event publication registry (outbox in the same transaction,
  delivered after commit, resubmitted by a job). Delivery is at least once, so every listener is
  idempotent and has a duplicate-delivery test. **Exception (owner, 2026-11-22):** `account/api/events/OrganizationPurging` is synchronous: published inside the organization purge transaction and handled by plain `@EventListener`s that delete the module's rows before the venues go (`docs/architecture/DB_SCHEMA.md`, account deletion).
- **Jobs:** db-scheduler, PostgreSQL-backed, started when the context is ready. Jobs are
  idempotent; their bodies are plain methods testable without the scheduler.
- **Errors:** expected outcomes are sealed return types; rule violations and failures are
  `DomainException` subclasses with stable codes, mapped once to RFC 9457. No Arrow, no `Result`.
- **Tenancy and concurrency:** every venue-scoped use case checks access through
  `account::api` `VenueAccess` first. Every venue-scoped method takes the venue ID. Spring MVC on
  virtual threads with blocking jOOQ; small HikariCP pool; bounded calls to external APIs. No RLS for
  now.
- **Domain modelling:** `stock` uses aggregates and value objects with invariants inside them;
  `pos` uses plain data classes; `reporting`'s calculations live in `reporting/domain` (pure
  functions over facts) and its screen queries in `reporting/infra/persistence`, over reporting's
  own tables only.

### 7. Enforcement

| Rule | Gate in C3 | How |
|------|------------|-----|
| Direction (§2) | ArchUnit | The table in §2 as a rule per business module (`account`, `pos`, `reporting`, `stock`), plus a rule that every class of a business module lives in one of the §2 packages (else the layer rule never sees it); `common` is exempt from both but not from R3–R7. Package patterns are anchored to the module. |
| R1 | ArchUnit | `DatabaseAccessRules`: each module only its own `com.onemind.db.<schema>` package; the `reporting → pos` exception is removed. |
| R2 | ArchUnit + reviewer | Gate: Spring beans (`@Service`/`@Component`) in `application` have at most 5 constructor parameters not on the allowlist (`Clock`, `TransactionTemplate`), and no name ending in `Service`. Reviewer: "one screen or job per class". |
| R3 | ArchUnit | `org.jooq..` and `com.onemind.db..` accessed only from `..infra.persistence..`. |
| R4 | ArchUnit (partial) + reviewer | Gate: public methods in `infra.persistence` don't return `org.jooq.Record` subtypes or `Result`. Reviewer: records hidden inside generic return types. |
| R5 | ArchUnit | `com.onemind.api.v1..` accessed only from `..web..`. |
| R6 | ArchUnit | `..domain..` depends on no `org.springframework..`, `org.jooq..`, `tools.jackson..` or `com.fasterxml.jackson..`. |
| R7 | ArchUnit | `@Transactional` and `TransactionTemplate` used only in `..application..` of business modules and outside `infra`/`web` in `common`. |
| R8 | Spring Modulith | `ApplicationModules.verify()` with `allowedDependencies` and named interfaces `api` and `events`. |
| R9 | ArchUnit (allowlist) + reviewer | Gate: interfaces are sealed result types or on the allowlist by fully qualified name (`pos.infra.client.PosProvider`, `stock.infra.client.ReceiptExtractor`; `LlmClient` is added with its package when the assistant's module is decided). Reviewer: a new entry needs a second implementation or a decision here. |

Notes for the C3 rules:

- **One `layeredArchitecture()` per business module**, with
  `consideringOnlyDependenciesInLayers()`: a single rule over all modules would flag allowed
  cross-module calls (for example `reporting.application` → `account.api`), which Spring Modulith
  checks instead (R8).
- The `api` layer is the **exact package** `<module>.api` (no trailing `..`), so `<module>.api.events`
  is its own layer.
- `infra.persistence` and `infra.client` are **two layers**, so a rule can keep jOOQ out of
  `client` (R3) and `persistence` and `client` independent of each other.

Every ArchUnit rule gets a fixture that violates it and a test proving the rule catches it, as
`DatabaseAccessRules` has today.

## Alternatives considered

- **Keep ADR 0009 as is:** no move cost, but the weaknesses above grow with every Phase 4–6
  feature, and several rules stay uncheckable.
- **Full hexagonal (ports in `domain`, adapters in `infra`, dependency inversion everywhere):**
  clean direction, but an interface per repository with one implementation each, which is what R9
  rules out. Repository fakes aren't needed: integration tests run on Testcontainers PostgreSQL.
- **Rename packages (`adapter/in/web`, `adapter/out/persistence`, …):** clearer for hexagonal
  readers, but it touches every file and adds nothing the split `infra` doesn't already give.
- **Keep `reporting` reading `pos` tables (ADR 0009 exception) and only add a check:** fastest
  rollups, but it couples reporting to pos's table layout, the coupling ADR 0009 itself warns
  about. The read facade keeps the SQL inside `pos` at a small, measurable cost.
- **Fat events (`BillsSynced` carrying all bill lines):** no synchronous dependency on `pos`, but
  large payloads in `platform.event_publication` and duplicated data on every resubmission.
- **Gradle modules per business module:** compile-time boundaries, but per-module jOOQ codegen and
  build complexity while the boundaries are still moving (same reason as ADR 0009).

## Consequences

- **C2 (code moves, after the reviewer audit):** move current code to the new packages without
  behaviour changes. Known items:
  - `account/infra/AuthRepositories`, `VenueAccessRepository` → `account/infra/persistence`.
  - `account/infra/IdTokenVerifier` → `account/infra/client`.
  - `account/infra/AuthJobs` → `application`.
  - `common/idempotency/infra` → `common/idempotency/infra/persistence`.
  - `EmailSignIn` (9 dependencies) split into `StartEmailSignIn` and `VerifyEmailSignIn`;
    `IdTokenSignIn` stays one class (done in C2): after the shared rate limit and hash moved out it
    has 5 collaborators, serves one screen (A1), and Apple and Google differ only in the nonce check and
    the display name, so a split would add two thin classes and a helper without removing code.
  - `SessionTokenService` split into use cases (start session as a helper of the sign-ins,
    `RefreshTokens`, `Logout`).
  - `VenueAccess`: interface plus `VenueAccessService` becomes one concrete facade class in
    `account/api` (R9).
  - `EmailSender` (one SMTP implementation, a test fake) becomes a concrete class in
    `common/email` (R9); tests replace the bean instead of implementing an interface.
  - `common/money/CurrencyConverter` (a jOOQ converter, R3) → `common/money/infra/persistence`,
    with its class name updated in the jOOQ codegen config in `build.gradle.kts`.
  - `common/error/ApiExceptionHandler` and `Problems` (they use the generated `FieldError`, R5) →
    `common/web`.
  - Job beans: `AuthJobs` → `account/application`; `IdempotencyJobs` and `EventPublicationJobs`
    stay at their `common` area's root.
  - Paths in docs: ADR 0004 implementation notes and `backend/AGENTS.md` ("Where it lives",
    "Architecture") follow the moves in the same PR.
  - `@ApplicationModule(allowedDependencies = …)` on every module.
- **C3 (done):** the gates in §7 live in `backend/src/test/.../architecture/` (`StructureRules`,
  `DatabaseAccessRules`, `ModuleBoundaryTests`); fixtures in `com.onemind.archfixture` (outside the
  application package, so component scanning never loads them) prove each rule catches its violation.
- **Docs:** `backend/AGENTS.md` ("Architecture", "Rules", the AI rule on `LlmClient` and the
  hosting list of interfaces) follows this ADR in C2; ADR 0009 is kept for history, superseded.
  ADR 0008's "external services sit behind interfaces" now reads: `FileStorage` and `EmailSender`
  are concrete adapters in `infra/client` (provider switch by configuration); `ReceiptExtractor`
  (ADR 0007) and `LlmClient` stay interfaces (R9).
- New work in Phases 4–6 starts in this structure: sync use cases in `pos/application`, the pos read
  facade in `pos/api`, and `PosProvider` in `pos`. **Owner decision (2026-11-21):** the real
  Storyous API is not connected for now. Phase 4 builds `PosProvider` with a mock/fixture
  implementation (recorded or generated POS data) in `pos/infra/client`; the real Storyous client
  comes later as a second implementation behind the same interface, which is what justifies the
  interface under R9.

### Owner decisions (2026-11-21)

1. **Module graph:** confirmed as in §4. `reporting` and `stock` depend on `account::api` and
   `pos::api`; `reporting` also on `stock::api` and `stock::events` (`possibleLoss`; an empty state,
   never 0, if stock isn't in the first release).
2. **R9:** interfaces only for `PosProvider`, `ReceiptExtractor` and `LlmClient`. `EmailSender` and
   `FileStorage` become concrete classes; the provider switch is configuration. Sealed result types
   stay allowed.
3. **R2:** at most 5 collaborators, counted on Spring beans in `application` only; `Clock` and
   `TransactionTemplate` are exempt through a fixed allowlist in the gate.
4. **POS data:** mock data only for now; `PosProvider` gets a mock/fixture implementation, the real
   Storyous client later behind the same interface.

### Owner decisions (2026-11-22)

1. **Module graph:** `pos`, `reporting` and `stock` also depend on `account::events`, so they can
   handle `OrganizationPurging` (organization purge after account deletion). `account` still depends
   on no business module, so the graph has no cycle.
2. **Synchronous event:** `OrganizationPurging` is the one event handled in the publisher's
   transaction (§6); chosen over an R9 interface exception and over `ON DELETE CASCADE` on venue
   foreign keys (which would later cascade into stock history, product rule 4).
