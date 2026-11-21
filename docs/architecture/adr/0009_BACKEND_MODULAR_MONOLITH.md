# 0009 · Backend architecture: modular monolith with Spring Modulith

- **Status:** Superseded by [0013](0013_BACKEND_MODULE_STRUCTURE.md) (once 0013 is accepted)
- **Date:** 2026-11-20
- **Deciders:** Kaan

## Context

The backend has a few clearly different parts: accounts and auth, a read-only copy of POS data,
reporting over that data (heavy SQL), and a stock domain with real business rules. One small team
builds it, it runs on one VM (ADR 0008), and it must stay easy to change. Practitioner reports
agree on a few points: wrong boundaries hurt more than weak enforcement; hexagonal layering pays off
only for complex domain logic or swappable infrastructure; database coupling (shared tables,
cross-module foreign keys) is not caught by code-level checks.

## Decision

### 1. Modules

One deployable, split into modules by business capability:

| Module | Owns | PostgreSQL schema |
|--------|------|-------------------|
| `account` | users, auth, organizations, **venues**, memberships, followed venues | `account` |
| `pos` | Storyous client, sync jobs, POS read model (bills, items, payments, discounts, deleted items, products, categories, tables, staff, shifts, Happy Hour rules) | `pos` |
| `reporting` | metric calculations, hourly rollup tables, screen queries (Today, Sales, Staff, Tables, …) | `reporting` |
| `stock` | stock items, locations, movements, receipts, counts, transfers, recipes, supplier aliases | `stock` |
| `common` | shared kernel: `Money`, `Quantity`, IDs, business-day calendar, errors, security, pagination | – |

- Each module has **its own PostgreSQL schema**. jOOQ generates one package per schema, so table
  access is visible in imports and can be checked by tests.
- Cross-module foreign keys are allowed only to `account.venue` and `account.app_user`.
- **One deliberate exception:** `reporting` may **read** `pos` tables (reports need joins). Only
  `pos` writes them. A test enforces that `reporting` uses only its own and the `pos` jOOQ packages.
- When `pos` discovers a venue during sync, it registers it through `account`'s API.

### 2. Boundary enforcement

- A single Gradle module; modules are top-level packages under `com.onemind.backend`.
- **Spring Modulith** (2.x, Boot 4 line) verifies the boundaries. Each module exposes only its
  `api` package (interfaces, DTOs, events). A test runs `ApplicationModules.of(...).verify()`
  plus our jOOQ-package rules, and is **mandatory in CI**: a failing boundary test blocks merge.
- Moving modules to separate Gradle modules later stays possible.
- Implementation: Spring Modulith 2.2.0 (BOM in `build.gradle.kts`). `api` packages are named
  interfaces via `package-info.java`; `common` is an open module. jOOQ rules are ArchUnit rules
  (ArchUnit comes with Spring Modulith) in `architecture/DatabaseAccessRules`, with fixture tests
  that prove they catch violations.

### 3. Layers inside a module (pragmatic hexagonal)

```
<module>/
├── api/          ← public: service interfaces, DTOs and events other modules may use
├── web/          ← @RestController implementing generated OpenAPI interfaces, mappers
├── application/  ← use cases, @Transactional boundaries, access checks
├── domain/       ← plain Kotlin model and rules (no Spring, no jOOQ)   (only where rules exist)
└── infra/        ← jOOQ repositories/queries, external clients, scheduled jobs
```

- **Ports and adapters only for external systems**: Storyous (`PosProvider`), Claude
  (`ReceiptExtractor`), file storage (`FileStorage`), email (`EmailSender`).
- `stock`: repository interfaces in `domain`, jOOQ implementations in `infra`.
- `reporting`: no domain layer and no repositories. jOOQ **query classes** return response models
  directly (CQRS read side).
- `pos`: plain data classes; it is an immutable copy of POS data.

### 4. Domain modelling (DDD-lite where it pays)

- `stock` uses aggregates (`Receipt`, `StockCount`, `Transfer`, `Recipe`, `StockItem`,
  `StockLocation`) and value objects (`Money`, `Quantity` with unit, typed IDs). Invariants live in
  the aggregate: a receipt adds stock only when approved, a transfer keeps the venue total, stock
  movements are append-only.
- Other modules don't use aggregates.

### 5. Communication between modules

- **Synchronous:** call another module's `api` service interface.
- **Asynchronous:** domain events (e.g. `BillsSynced(venueId, businessDays)` → reporting refreshes
  rollups, stock deducts theoretical usage).
- Events go through the **Spring Modulith event publication registry (JDBC)**: written in the same
  transaction as the change that caused them (outbox), delivered after commit, retried on failure.
  Listeners use `@ApplicationModuleListener`.
- **Delivery is at least once, so every listener must be idempotent** (e.g. usage is deducted once
  per bill item, keyed by bill item ID).

### 6. Background jobs

- **db-scheduler** (`db-scheduler-spring-boot-4-starter`), PostgreSQL-backed: recurring POS sync,
  rollup refresh, nightly jobs, one-off receipt extraction tasks, with retries and persistence.
- Pin a version verified at setup (16.12.0 had a packaging issue). Set up with 17.1.0; the scheduler
  starts only when the application context is ready (`delay-startup-until-context-ready`).

### 7. Errors and validation

- **Expected business outcomes** that the caller must handle are **sealed return types**
  (e.g. receipt matching: `Matched | NeedsMatch | Unreadable`).
- **Rule violations and unexpected failures** are exceptions from a sealed `DomainException`
  hierarchy with stable error codes, mapped once in a `@ControllerAdvice` to RFC 9457 Problem
  Details.
- No Arrow, no generic `Result` wrapper. Not-found lookups return nullable types.
- Request shape is validated by Bean Validation on generated DTOs; business invariants by `require`
  in domain constructors and methods.

### 8. Transactions, tenancy, concurrency

- One transaction per use case, opened in `application`. Calls to Claude or Storyous happen
  **outside** DB transactions.
- Every venue-scoped repository/query method takes a `VenueId`. Each use case checks venue access
  through `account`'s API first. Every endpoint gets a cross-venue access test. PostgreSQL row-level
  security is not used for now.
- Spring MVC on **virtual threads** with blocking JDBC/jOOQ; no coroutines, no WebFlux. Java 25
  includes JEP 491 (no pinning on `synchronized`); use PostgreSQL JDBC driver 42.6+.
- HikariCP pool small (start at 10–20), short connection timeout so overload fails fast.
  Concurrency to external APIs (Claude, Storyous) is bounded (semaphore / client limits).

## Alternatives considered

- **Gradle multi-module:** compile-time boundaries, but more build complexity (per-module jOOQ
  codegen) while boundaries are still young.
- **Strict hexagonal everywhere:** interfaces and mappers for every layer; ceremony without benefit
  for read-only POS data and reporting.
- **Microservices:** operational cost far beyond a one-VM product.
- **JobRunr:** good dashboard, heavier; our jobs are simple.
- **`Result`/Arrow error handling:** explicit, but fights Spring transactions and adds a library.

## Consequences

- Boundaries are checked by tests, so the boundary test must stay green and fast.
- Four schemas and jOOQ packages from day one; migrations name their schema explicitly.
- Listener idempotency must be designed and tested for each event.
