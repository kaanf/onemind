# backend/AGENTS.md

Kotlin + Spring Boot API for OneMind. Read the root [`../AGENTS.md`](../AGENTS.md) first.

> **Status:** the project is not scaffolded yet. Items marked *Proposed* follow
> `docs/architecture/README.md` and may change after the architecture discussion. If you scaffold
> the project, follow this file and update it with the real commands.

## Responsibilities

- Auth (email OTP, Apple, Google) and memberships/roles.
- Storyous connection and **read-only** sync into our database.
- All metric calculations (`docs/domain/METRICS.md`). The mobile app only displays.
- Stock engine (`docs/domain/STOCK.md`): movements, counts, transfers, order plan, invoices, recipes.
- AI services (`docs/ai/ASSISTANT.md`): chat with tools, briefing, suggestions, RAG, invoice OCR.
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

## Layout (proposed: modular monolith)

```
backend/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/libs.versions.toml
└── src/
    ├── main/kotlin/com/onemind/backend/
    │   ├── Application.kt
    │   ├── common/          ← money, time/business-day, errors, security, pagination
    │   ├── identity/        ← users, auth, memberships
    │   ├── pos/             ← PosProvider interface, storyous/ client, sync jobs, id mapping
    │   ├── sales/           ← bills, payments, discounts, read models, aggregations
    │   ├── floor/           ← tables
    │   ├── staff/           ← staff members, shifts
    │   ├── menu/            ← products, categories, happy hour rules
    │   ├── stock/           ← items, locations, movements, counts, transfers, order plan, invoices, recipes
    │   └── assistant/       ← chat, tools, briefing, suggestions, knowledge, ocr
    ├── main/resources/
    │   ├── application.yml
    │   └── db/migration/    ← Flyway V001__*.sql
    └── test/kotlin/...
```

Each context package has the same inner structure:

```
<context>/
├── api/          ← @RestController implementing generated OpenAPI interfaces, DTO ↔ domain mappers
├── application/  ← use cases / services, transactions
├── domain/       ← entities, value objects, domain rules (no Spring annotations)
└── infra/        ← jOOQ repositories, external clients, schedulers
```

Rules:

- Contexts talk through `application` services or domain events, never through another context's
  repositories or tables.
- `domain/` is plain Kotlin: no Spring, no persistence annotations. Unit-test it without Spring.
- Controllers are thin: validate, call one use case, map to DTO.
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
- Errors: domain exceptions mapped to RFC 9457 Problem Details in one `@ControllerAdvice`.
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

- Implement each metric in `docs/domain/METRICS.md` exactly once, in the `sales` (or relevant)
  context, with a unit test per definition using fixture bills (include a midnight-crossing
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
