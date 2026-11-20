# backend/AGENTS.md

Kotlin + Spring Boot API for OneMind. Read the root [`../AGENTS.md`](../AGENTS.md) first.

> **Status:** scaffolded (Spring Boot 4.2, Java 25). Architecture decided in ADR 0009. Items still
> marked *Proposed* may change.

## Responsibilities

- Auth (email OTP, Apple, Google) and memberships/roles.
- Storyous connection and **read-only** sync into our database.
- All metric calculations (`docs/domain/METRICS.md`). The mobile app only displays.
- Stock engine (`docs/domain/STOCK.md`): movements, receipts, counts, transfers, recipes.
- AI: receipt extraction in the first release (ADR 0007); the assistant later
  (`docs/ai/ASSISTANT.md`).
- The public API (`docs/api/`).

## Stack

- Kotlin (JVM 25 LTS), Spring Boot 4.x, Gradle Kotlin DSL with a version catalog
  (`gradle/libs.versions.toml`).
- PostgreSQL + Flyway + **jOOQ** ([ADR 0002](../docs/architecture/adr/0002_POSTGRES_FLYWAY_JOOQ.md)).
  **No JPA/Hibernate**: do not add `spring-boot-starter-data-jpa` or `@Entity` classes.
  *Proposed:* pgvector for embeddings.
- API: contract-first from `docs/api/openapi.yaml`
  ([ADR 0001](../docs/architecture/adr/0001_OPENAPI_CONTRACT_FIRST.md)). Controller interfaces
  and DTOs are generated with OpenAPI Generator (`kotlin-spring`, `interfaceOnly`); Jackson for JSON.
- Testing: JUnit 5, Kotest assertions or AssertJ, MockK, Testcontainers (PostgreSQL),
  Spring Boot test slices.
- **Docker is required to build** (jOOQ codegen and integration tests use Testcontainers).

## Architecture ([ADR 0009](../docs/architecture/adr/0009_BACKEND_MODULAR_MONOLITH.md))

Modular monolith, boundaries verified by **Spring Modulith**. Read ADR 0009 before adding a
module, a cross-module call, an event or a table.

```
backend/src/main/kotlin/com/onemind/backend/
├── BackendApplication.kt
├── common/      ← shared kernel: Money, Quantity, typed IDs, business-day calendar, errors, security, pagination
├── account/     ← users, auth, organizations, venues, memberships          (schema: account)
├── pos/         ← Storyous client, sync jobs, POS read model               (schema: pos)
├── reporting/   ← metrics, hourly rollups, screen queries                  (schema: reporting; reads pos)
└── stock/       ← items, locations, movements, receipts, counts, transfers, recipes (schema: stock)
```

Inside each module:

```
<module>/
├── api/          ← PUBLIC: service interfaces, DTOs and events other modules may use
├── web/          ← @RestController implementing generated OpenAPI interfaces, mappers
├── application/  ← use cases, @Transactional, venue access checks
├── domain/       ← plain Kotlin model and rules (only where rules exist, e.g. stock)
└── infra/        ← jOOQ repositories/queries, external clients, scheduled jobs
```

Rules:

- A module may use another module **only through its `api` package**: never its `application`,
  `domain`, `infra` or `web` classes. The Modulith boundary test enforces this and must pass in CI.
- **Each module owns one PostgreSQL schema** and uses only its own jOOQ package. Exception:
  `reporting` may read (never write) `pos` tables. Cross-module foreign keys only to
  `account.venue` and `account.app_user`.
- Ports/adapters only for external systems: `PosProvider`, `ReceiptExtractor`, `FileStorage`,
  `EmailSender`. Don't add interfaces for internal classes without a reason.
- `stock` uses aggregates and value objects; invariants live in the aggregate. `reporting` uses
  jOOQ query classes that return response models directly (no domain layer). `pos` uses plain data
  classes.
- Async work between modules uses domain events via the Spring Modulith event publication registry
  (`@ApplicationModuleListener`). Delivery is at least once: **every listener must be
  idempotent**, with a test that delivers the same event twice.
- Background jobs use **db-scheduler**. Jobs must be idempotent and safe to retry.
- `domain/` is plain Kotlin: no Spring, no jOOQ. Unit-test it without Spring.
- Controllers are thin: validate, call one use case, map to DTO.
- **Follow the API rules in `docs/api/README.md`** ([ADR 0010](../docs/architecture/adr/0010_API_DESIGN.md)):
  UUIDv7 IDs (never treated as secrets), minor-unit money, decimal-string quantities and rates,
  RFC 9457 errors with a stable `code` and English `detail`, no PATCH, `version` → `409`,
  `Idempotency-Key` on creating POSTs, `404` for other venues' resources.
- **API changes start in `docs/api/openapi.yaml`.** Never hand-write a request/response DTO
  that the spec should generate. Never edit generated code (`build/generated`).
- Inside `/api/v1` changes are additive only (see ADR 0001). CI fails on breaking spec changes.

## Coding conventions

- Kotlin official style; ktlint or detekt enforced in CI (*Proposed*).
- Immutable data classes for DTOs and value objects. Use `value class` for IDs
  (`VenueId`, `BillId`) and `Money`.
- **Money:** `Money(amountMinor: Long, currency: Currency)`. Never `Double`. Rounding rules in
  one place.
- **Time:** store `Instant` (UTC, `timestamptz`). Business-day logic only in
  `common/time/BusinessDayCalendar` using the venue's `ZoneId`. Inject a `Clock`; never call
  `Instant.now()` directly (tests need fixed time).
- **Tenancy:** every venue-scoped repository method takes a `VenueId`. Authorisation check
  (user ↔ venue membership, role) happens in the application layer for every request. Add a test
  for cross-venue access on every new endpoint.
- Null-safety: no `!!` in production code.
- **Errors:** expected business outcomes the caller must handle are **sealed return types**
  (`Matched | NeedsMatch | Unreadable`). Rule violations and unexpected failures are exceptions
  from the sealed `DomainException` hierarchy (stable error codes), mapped once to RFC 9457 Problem
  Details in a `@ControllerAdvice`. No Arrow, no generic `Result` wrapper.
- **Transactions:** one per use case, in `application`. Never call Claude or Storyous inside a DB
  transaction.
- **Concurrency:** Spring MVC on virtual threads with blocking jOOQ/JDBC. No coroutines, no
  WebFlux. Keep the HikariCP pool small (10–20) and bound concurrent calls to external APIs.
- Logging: structured (JSON) logs, no PII or secrets, include `venueId` and request ID.
- Configuration via `application.yml` + environment variables. Secrets never in the repo.

## Auth rules ([ADR 0004](../docs/architecture/adr/0004_OWN_JWT_AUTH.md))

- Access JWT 15 min (asymmetric key, `kid`), refresh token 30 days, opaque, stored hashed, rotated on
  every use, reuse → revoke the family.
- Roles and venue access are loaded per request from memberships, never trusted from the token.
- Email code: 6 digits, hashed, 10 min, max 5 attempts, resend after 30 s, rate-limited, always
  `202`. Never log codes or tokens.
- Apple/Google tokens verified against their JWKS with `iss`/`aud`/expiry (and nonce for Apple).
- Account deletion endpoint is required (App Store).
- Auth code changes need tests for each rule above.

## Live data and streaming ([ADR 0005](../docs/architecture/adr/0005_POLLING_AND_SSE.md))

- Live read endpoints return `ETag` and honour `If-None-Match` (`304`), and include `dataAsOf`.
- Assistant answers stream as SSE (`SseEmitter`, virtual threads): events `delta`, `block`,
  `citation`, `suggestedReplies`, `done`, `error`. Persist the final message so it can be reloaded.

## Offline support ([ADR 0006](../docs/architecture/adr/0006_OFFLINE_STRATEGY.md))

- Stock count write endpoints require an `Idempotency-Key` header and must accept late and
  out-of-order lines (last write wins per `(countId, stockItemId)` by counted-at time; keep every
  submission for audit).
- Count completion recomputes variance against movements at processing time.

## POS sync rules

- Storyous client exposes **read methods only**. A test must fail if a non-GET call is added.
- Upserts idempotent by `(provider, externalId)`.
- Sync never blocks API requests. Report progress per step for `/pos/sync/status`.
- After sync batches, publish events (`BillsSynced(venueId, businessDays)`) that trigger
  aggregation refresh, stock usage and suggestion detectors.

## Metrics rules

- Implement each metric in `docs/domain/METRICS.md` exactly once, in the `reporting`
  module, with a unit test per definition using fixture bills (include a midnight-crossing
  business day and a DST day).
- If a metric definition is marked **(?)**, implement the documented default and reference the
  open-question ID in a comment.

## AI rules

- **First release:** the only AI call is receipt extraction, behind `ReceiptExtractor`
  ([ADR 0007](../docs/architecture/adr/0007_RECEIPT_EXTRACTION_WITH_CLAUDE.md)): Claude Haiku 5.5
  with structured output, one Sonnet 5.5 retry on validation failure, official Anthropic Java SDK,
  token usage stored per extraction, daily cap per venue. The model output is validated before use.
- The rules below apply to the assistant when it is built (later).

- The LLM never computes numbers. Numbers come from tools that call our services.
- Every answer returns citations. Document retrieval filters by role and venue **before** the
  prompt is built.
- Prompts live in versioned resource files, not inline strings scattered through the code.
- Wrap the provider behind an interface (`LlmClient`) so the model/provider can change.
- Golden-question tests run against fixture data for any prompt/tool change.

## Database

- Flyway migrations are append-only. Never edit an applied migration.
- jOOQ classes are generated from the migrated schema (Testcontainers → Flyway → codegen) into
  `build/generated-src/jooq`. After adding a migration, regenerate; never edit generated code.
- jOOQ `Record` types never leave `infra/`. Repositories return domain objects.
- Use jOOQ's DSL for all SQL, including reports (window functions, CTEs). No raw SQL strings except
  in migrations.
- Bulk sync writes: `insertInto(...).onConflict(...).doUpdate()` with batching.
- Reports read from hourly rollup tables refreshed after each sync batch; "now" values come from
  base tables. Rollups must be rebuildable from base tables.
- Snake_case table and column names. Every venue-scoped table has `venue_id` with an index.
- Money columns: `bigint` minor units + `char(3)` currency (or a fixed venue currency). Converters
  to `Money` / `Instant` are defined once in `common/`.
- Stock movements table is insert-only (no UPDATE/DELETE in application code).

## Testing

- Unit tests for domain logic (metrics, stock formulas, order plan, matching).
- Integration tests with Testcontainers PostgreSQL for repositories and migrations.
- API tests (`@SpringBootTest` + `WebTestClient`/`MockMvc`) for auth and tenancy.
- Storyous client tests against recorded fixtures (no live calls in CI).

## Commands

Run from `backend/`. Docker is required for tests (Testcontainers) and, once added, jOOQ codegen.

```bash
docker compose -f ../infra/docker-compose.yml up -d   # local PostgreSQL 18
./gradlew bootRun                                     # run the API on :8080 (health: /actuator/health)
./gradlew test                                        # tests (start PostgreSQL via Testcontainers)
./gradlew build                                       # compile + test + jar
./gradlew bootTestRun                                 # run the app with a Testcontainers PostgreSQL (no compose needed)
```

Not set up yet (next steps): OpenAPI code generation, jOOQ code generation, ktlint/detekt.

## Hosting ([ADR 0008](../docs/architecture/adr/0008_HETZNER_HOSTING_ZERO_COST_START.md))

- Production is one Hetzner VM with Docker Compose (Caddy, backend, PostgreSQL). Deployment,
  backup and restore scripts live in `../infra/`; never change the server by hand.
- External services sit behind interfaces so they can be swapped by configuration:
  `FileStorage` (S3 API; MinIO locally), `EmailSender`, `ReceiptExtractor`.
- Expose a health endpoint (Spring Boot Actuator `health`) for uptime monitoring; no other
  actuator endpoints public.
- Configuration only via environment variables in production.

## Definition of done (backend)

- Tests added and passing; no new warnings.
- OpenAPI/`docs/api` updated for any endpoint change.
- `docs/domain/*` updated for any rule or metric change.
- Migration included for schema changes, and it is additive (or an ADR explains why not).
