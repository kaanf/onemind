# 0013 · Backend module internal structure

- **Status:** Proposed
- **Date:** 2026-11-21
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
    ├── persistence/     ← jOOQ repositories and queries, returning domain or application types
    └── client/          ← adapters to external systems (IdTokenVerifier, Storyous client, SMTP)
```

**Dependency direction:** `web → application → domain ← infra`.

- `application` uses `infra/persistence` and `infra/client` classes **directly**. Under R9 there
  are no repository interfaces in `domain`, so `infra` doesn't implement domain ports. It only uses
  domain types as inputs and results.
- `domain` depends on nothing in the module and only on the plain-Kotlin parts of `common` (`Money`,
  `Quantity`, IDs, `DomainException`).
- `api` is a thin facade: plain classes that delegate to `application` use cases, plus DTOs that
  carry only Kotlin and `common` types. Other modules never see `application` or `domain` classes.
- `web` calls `application` only, never `infra`. Controllers turn generated DTOs into use-case
  arguments and results into generated DTOs.
- Wiring that belongs to a use case lives next to it in `application`: db-scheduler task beans (the
  body is a plain use-case method) and `@ApplicationModuleListener` methods (each is idempotent,
  ADR 0009 §5 still applies).
- Where the shared kernel needs the same split (for example `common/idempotency/infra`), it gets it:
  `common/<area>/infra/persistence`.

### 3. Rules

| ID | Rule |
|----|------|
| **R1** | A module touches only its own schema's generated jOOQ classes (`com.onemind.db.<own schema>`; `common` owns `platform`). Data of another module comes through that module's `api` or its events. No cross-schema joins in code. |
| **R2** | Use-case classes, not growing `*Service` classes. A use case covers one screen or job. More than **5 constructor dependencies** means it must be split. |
| **R3** | `DSLContext` and other jOOQ types (`org.jooq..`, `com.onemind.db..`) are used only in `infra/persistence`. |
| **R4** | jOOQ `Record` types never leave `infra/persistence`: repositories return domain or application types, or plain Kotlin values. |
| **R5** | Generated OpenAPI models (`com.onemind.api.v1..`) never leave `web`. |
| **R6** | `domain` imports no Spring, jOOQ or Jackson. |
| **R7** | Transactions (`@Transactional`, `TransactionTemplate`) only in `application`. One per use case; no Claude or Storyous call inside one. |
| **R8** | The module graph is explicit: every module declares `@ApplicationModule(allowedDependencies = …)` (§4). Changing it changes this ADR. |
| **R9** | No interface without a second implementation. **Exception:** `PosProvider` (Storyous now, other POS systems later). Test doubles don't count as a second implementation. |

### 4. Module graph (R8)

| Module | Type | Allowed dependencies | Why |
|--------|------|----------------------|-----|
| `common` | open | – | Shared kernel; every module may use every `common` package. `common` itself uses no business module. |
| `account` | base | – | Users, venues and memberships. The others depend on it; it depends on none of them. |
| `pos` | – | `account::api` | Registers the venues it discovers and checks venue access for sync status. Uses no other business module. |
| `reporting` | – | `account::api`, `pos::api`, `pos::events` | Venue access; reads POS facts through the pos read API; refreshes rollups on `BillsSynced`. |
| `stock` | – | `account::api`, `pos::api`, `pos::events` | Venue access; sold items and the product catalog for usage and recipes; deducts usage on `BillsSynced`. |

`account::api` and `account::events` are named interfaces, as are `pos::api` and `pos::events`.
Nothing depends on `reporting` or `stock`. Cycles are forbidden (Spring Modulith already checks
that).

**Deviation from the brief ("reporting and stock only on `pos::events`"):** both also need venue
access (`account::api`, AGENTS.md rule 7), and both need POS facts that an event can't carry
reasonably (bill lines for a business day are thousands of rows, and the event payload is stored in
`platform.event_publication`). So they read through the pos read API below. Events stay small
signals (`BillsSynced(venueId, businessDays)`).

### 5. POS read API (replaces reporting's table access)

`pos/api` gets a read facade, owned and versioned by `pos`, that returns DTOs, never jOOQ types:

- `PosReadModel.billLines(venueId, businessDays): Sequence<BillLineFact>` streams paid bill lines
  (bill, business day, paid at, desk, staff, product, quantity, gross/net minor units, discount,
  refunded/deleted flags) in pages, ordered by bill.
- `PosReadModel.payments(venueId, businessDays)`, `deletedItems(…)`, `products(venueId)`,
  `desks(venueId)`, `staff(venueId)` for the other rollups and screens.
- "Now" figures that need open bills (Today, ADR 0005) go through the same facade
  (`openTabs(venueId)`), so `reporting` never reads `pos` tables, not even for live data.

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
  idempotent and has a duplicate-delivery test.
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
| Direction (§2) | ArchUnit | Layer rule per module: `web` → `application` → `domain`/`infra`; `infra` → `domain`; `domain` → nothing in the module; `web` doesn't access `infra`. |
| R1 | ArchUnit | `DatabaseAccessRules`: each module only its own `com.onemind.db.<schema>` package; the `reporting → pos` exception is removed. |
| R2 | ArchUnit + reviewer | Gate: classes in `application` have at most 5 constructor parameters and no name ending in `Service`. Reviewer: "one screen or job per class". |
| R3 | ArchUnit | `org.jooq..` and `com.onemind.db..` accessed only from `..infra.persistence..`. |
| R4 | ArchUnit (partial) + reviewer | Gate: public methods in `infra.persistence` don't return `org.jooq.Record` subtypes or `Result`. Reviewer: records hidden inside generic return types. |
| R5 | ArchUnit | `com.onemind.api.v1..` accessed only from `..web..`. |
| R6 | ArchUnit | `..domain..` depends on no `org.springframework..`, `org.jooq..`, `tools.jackson..` or `com.fasterxml.jackson..`. |
| R7 | ArchUnit | `@Transactional` and `TransactionTemplate` used only in `..application..`. |
| R8 | Spring Modulith | `ApplicationModules.verify()` with `allowedDependencies` and named interfaces `api` and `events`. |
| R9 | Reviewer | Not checkable mechanically (a second implementation can arrive later); the reviewer lane checks every new interface. |

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
    `IdTokenSignIn` (6) into `SignInWithApple` and `SignInWithGoogle` over a shared helper (R2).
  - `SessionTokenService` split into use cases (start session as a helper of the sign-ins,
    `RefreshTokens`, `Logout`).
  - `VenueAccess`: interface plus `VenueAccessService` becomes one concrete facade class in
    `account/api` (R9).
  - `EmailSender` (one SMTP implementation, a test fake) becomes a concrete class, unless the owner
    keeps it as an exception (see open points).
  - `@ApplicationModule(allowedDependencies = …)` on every module.
- **C3:** the ArchUnit and Modulith gates in §7, with fixture tests, mandatory in CI.
- **Docs:** after approval, `backend/AGENTS.md` ("Architecture", "Rules") and
  `docs/architecture/README.md` describe this structure; ADR 0009 is kept for history and marked
  superseded. ADR 0008's "external services sit behind interfaces" now reads "behind an adapter in
  `infra/client`; an interface only with a second implementation (R9)".
- New work in Phases 4–6 starts in this structure: the Storyous client in `pos/infra/client`
  behind `PosProvider`, sync use cases in `pos/application`, the pos read facade in `pos/api`.

### Open points for the owner

1. **Module graph:** `reporting` and `stock` also depend on `account::api` (venue access) and
   `pos::api` (read facade), not only on `pos::events` (§4). Confirm.
2. **R9 and existing ports:** `EmailSender` has one production implementation; ADR 0008 lists it,
   `FileStorage` and `ReceiptExtractor` as swappable interfaces. Under R9 they become concrete
   adapters until a second implementation exists (the provider switch for email is SMTP
   configuration, `docs/architecture/README.md` item 11). Confirm, or name them as exceptions
   next to `PosProvider`.
3. **R2 threshold:** "more than 5 constructor dependencies" counts `Clock` and
   `TransactionTemplate`, which almost every use case needs. Keep 5, or count only collaborators?
