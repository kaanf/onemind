# backend/AGENTS.md

Kotlin + Spring Boot API for OneMind. Read the root [`../AGENTS.md`](../AGENTS.md) first.

> **Status:** scaffolded (Spring Boot 4.2, Java 25). Architecture decided in ADR 0013 (replaces ADR 0009). Items still
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
  and DTOs are generated with OpenAPI Generator (`kotlin-spring`, `interfaceOnly`, Spring Boot 4 /
  Jackson 3) on every build into `build/generated/openapi`: interfaces in `com.onemind.api.v1.web`,
  DTOs in `com.onemind.api.v1.model` (outside `com.onemind.backend`, so Spring Modulith does not
  see them as a module). Jackson for JSON.
- Testing: JUnit 5, Kotest assertions or AssertJ, MockK, Testcontainers (PostgreSQL),
  Spring Boot test slices.
- **Docker is required to build** (jOOQ codegen and integration tests use Testcontainers).

## Architecture ([ADR 0013](../docs/architecture/adr/0013_BACKEND_MODULE_STRUCTURE.md))

Modular monolith, boundaries verified by **Spring Modulith**. Read ADR 0013 (it replaces ADR 0009)
before adding a module, a package, a cross-module call, an event, an interface or a table.

```
backend/src/main/kotlin/com/onemind/backend/
├── BackendApplication.kt
├── common/      ← shared kernel (open module): Money, errors, security, web, idempotency, events, …
├── account/     ← users, auth, organizations, venues, memberships          (schema: account)
├── pos/         ← POS read model and sync; mock data first (PosProvider)   (schema: pos)
├── reporting/   ← metrics, hourly rollups, screen queries                  (schema: reporting)
└── stock/       ← items, locations, movements, receipts, counts, transfers, recipes (schema: stock)
```

Inside each business module (ADR 0013 §2):

```
<module>/
├── api/                 ← PUBLIC facade (the only synchronous entry for other modules) and its DTOs
│   └── events/          ← PUBLIC: events this module publishes
├── web/                 ← @RestController implementing generated OpenAPI interfaces; mapping only
├── application/         ← one use case per screen or job; @Transactional; job and listener beans
├── domain/              ← pure Kotlin rules and calculations
└── infra/
    ├── persistence/     ← jOOQ repositories/queries returning domain types or their own row classes
    └── client/          ← adapters to external systems (IdTokenVerifier, PosProvider implementations)
```

**New backend code follows the structure of the `account` module.** Canonical slice:
`account/web/AuthController` → `account/application/RefreshTokens` →
`account/infra/persistence/RefreshTokenRepository`.

The direction (`web`/`api` → `application` → `domain` ← `infra`) and rules R1–R9 of ADR 0013 are
build gates in `architecture/` (`ModuleBoundaryTests` runs them on the code; `StructureRulesTest` and
`DatabaseAccessRulesTest` prove each one against fixtures). Product rules 1, 5 and 6 are gated the
same way (`ProductRules`, `ProductRulesTest`). On the real code no rule may pass on an empty scope
(only `posIsReadOnly` allows it until `pos` has code), and `ModuleBoundaryTests` checks the import
holds an anchor class per module and layer; add one when a new module or layer gets code. A red gate
names the rule; read it in
ADR 0013 §3 (product rules: root `AGENTS.md`) instead of working around it. What the gates don't check:

- Cross-module foreign keys only to `account.venue` and `account.app_user` (R1, database level).
- One use case per screen or job (R2; the gate checks the 5-collaborator limit and `*Service` names).
- A new interface needs a second implementation or an ADR 0013 exception (R9; the gate holds the
  allowlist `PosProvider`, `ReceiptExtractor`, `LlmClient`).
- Module graph (R8): `@ApplicationModule(allowedDependencies = …)` in each module's
  `package-info.java` (`src/main/java`), equal to ADR 0013 §4 (pinned by `ModuleBoundaryTests`).
  `common` must be listed although it is open. A new module needs `package-info.java` for the module,
  `api` and `api/events`, and entries in ADR 0013, `DatabaseAccessRules` and `ModuleBoundaryTests`.
- `stock` uses aggregates and value objects; invariants live in the aggregate. `reporting` keeps its
  calculations in `reporting/domain` and its queries in `reporting/infra/persistence`, over its own
  tables. `pos` uses plain data classes.
- Async work between modules uses events in `api/events` via the Spring Modulith event publication
  registry (`@ApplicationModuleListener`). Delivery is at least once: **every listener must be
  idempotent**, with a test that delivers the same event twice. Failed publications are resubmitted
  by a job (`common/events/EventPublicationJobs`), so a listener may see the same event again later.
- Background jobs use **db-scheduler**. Jobs must be idempotent and safe to retry. Register a job
  as a task bean (`Tasks.recurring(...)` / `Tasks.oneTime(...)`) in `application` (in `common`: at
  the area's root) and keep its body in a plain method you can test without the scheduler.
- Kotlin classes are final: a class proxied only because of a method annotation (for example an
  `@ApplicationModuleListener` method in a class without `@Component`) must be `open`.
- Controllers are thin: validate, call one use case, map to DTO.
- Controllers implementing a generated interface are served under `/api/v1` by
  `common/web/ApiPathConfig` (the generated paths don't include the server URL).
- **Follow the API rules in `docs/api/README.md`** ([ADR 0010](../docs/architecture/adr/0010_API_DESIGN.md)):
  UUIDv7 IDs (never treated as secrets), minor-unit money, decimal-string quantities and rates,
  RFC 9457 errors with a stable `code` and English `detail`, no PATCH, `version` → `409`,
  `Idempotency-Key` on creating POSTs, `404` for other venues' resources.
- `Idempotency-Key` is handled once, by `common/idempotency/IdempotencyFilter` (per user,
  `platform.idempotency_record`), for POSTs whose handler declares the header: an endpoint only adds
  the shared `IdempotencyKey` header parameter in the spec; don't re-implement deduplication in
  controllers. Never declare it on an endpoint whose response holds secrets (tokens): the response is
  stored in plain text for 24 h.
- **API changes start in `docs/api/openapi.yaml`.** Never hand-write a request/response DTO
  that the spec should generate. Never edit generated code (`build/generated`).
- Inside `/api/v1` changes are additive only (see ADR 0001). CI fails on breaking spec changes.

## Coding conventions

- Style: ktlint (`ktlint_official` code style, see `../.editorconfig`), checked by `./gradlew build`
  and in CI. Run `./gradlew ktlintFormat` before committing. detekt is added once 2.0 is stable
  (1.23.x does not support Kotlin 2.2+).
- Versions live in `gradle/libs.versions.toml`; no version strings in `build.gradle.kts`.
- Immutable data classes for DTOs and value objects. Use `value class` for IDs
  (`VenueId`, `BillId`) and `Money`.
- **Money:** `Money(amountMinor: Long, currency: Currency)`; sub-minor prices `BigDecimal`. Rounding
  rules in one place. Gated (`architecture/ProductRules`, rule 5): no `Float`/`Double` in a
  money-named class, field, method or parameter (whole words from `docs/api/lint/money-words.json`,
  plurals too; names ending in `Rate`/`Ratio`/`Percent`/`Pct`/`Share`/`Count`/`Quantity`/`Number`/
  `Id`/`Index`, singular or plural, are not money), no `BigDecimal` from a `Double`. Parameter names
  come from `javaParameters` in `build.gradle.kts`; a method or constructor the rule can't reflect
  fails it. Not caught: a `Double` money value under a name without a money word.
- **Time:** store `Instant` (UTC, `timestamptz`). Business days only via
  `common/time/BusinessDayCalendar` (venue `ZoneId` + `businessDayStart`). Gated (rule 6): only
  `common/time/ClockConfig` reads the system clock; elsewhere inject `Clock` (`Instant.now(clock)`).
  Only `common/time` turns an instant into a local date, time or hour (`atZone`, `LocalDate.ofInstant`,
  `toLocalDate`, `hour` on zoned values); `atOffset` for API mapping is fine. Code that needs a local
  hour or time of day (hourly charts) gets it from a `BusinessDayCalendar` helper (e.g. `localHour`),
  added with its first use; never relax the gate or add an exception for it.
- **Tenancy:** every venue-scoped repository method takes a `VenueId`. Every venue-scoped use case
  first calls `account/api/VenueAccess.requireAccess(userId, venueId, role?)` with
  `CurrentUser.id()`: it loads the membership per request and throws `404 VENUE_NOT_FOUND` for a
  venue that isn't the user's, doesn't exist or is `DISABLED` and `403 VENUE_ROLE_REQUIRED` for a too-low role.
  Pass no role (any member) or `VenueRole.OWNER`; STAFF permissions wait for P2. Every
  venue-scoped endpoint gets a cross-venue test: build a `testing/TenancyFixture` (two
  organizations, owner/staff/other owner/outsider) and call
  `fixture.expectNoCrossVenueAccess { venueId, token -> mockMvc.get(...) }`
  (example: `account/VenueAccessTests`). The helper checks status codes only: once venue data exists
  (phase 4/5), add a data-leak check (an "All venues" aggregate and list endpoints must not include
  other organizations' venues) to the fixture.
- Null-safety: no `!!` in production code.
- **Errors:** expected business outcomes the caller must handle are **sealed return types**
  (`Matched | NeedsMatch | Unreadable`). Rule violations and unexpected failures are exceptions
  from the sealed `DomainException` hierarchy (stable error codes), mapped once to RFC 9457 Problem
  Details in `common/web/ApiExceptionHandler`. A module's exception extends one of the categories
  in `common/error/DomainException.kt` (`InvalidRequestException` 400, `ForbiddenException` 403,
  `NotFoundException` 404, `ConflictException` 409, `RuleViolationException` 422) with its own
  `code`, an English `detail` without personal data and optional `params`. A servlet filter that
  must reject a request passes the exception to the `handlerExceptionResolver` bean
  (`resolveException(request, response, null, e)`), so the problem looks like any other. No Arrow,
  no generic `Result` wrapper.
- **Transactions:** one per use case (where: R7, gated). Never call Claude or Storyous inside a DB
  transaction.
- **Concurrency:** Spring MVC on virtual threads with blocking jOOQ/JDBC. No coroutines, no
  WebFlux. Keep the HikariCP pool small (10–20) and bound concurrent calls to external APIs.
- Logging: Spring Boot structured logging (`logging.structured.format.console: ecs`, JSON to stdout),
  Micrometer Tracing with the OpenTelemetry bridge and W3C `traceparent` (no span exporter yet).
  MDC fields `traceId`, `spanId` and `requestId` (the client's `X-Request-Id`, set by
  `common/observability/RequestIdFilter`, only if it is a UUID) appear on every log line; `traceId`
  also goes into error responses. Tests that need a real trace use `@AutoConfigureTracing`. No
  personal data or secrets in logs or Sentry events; log IDs (ADR 0012).
- Configuration via `application.yml` + environment variables; profiles `local` and `prod`.
  Secrets never in the repo.

## Auth rules ([ADR 0004](../docs/architecture/adr/0004_OWN_JWT_AUTH.md))

- Access JWT 15 min (asymmetric key, `kid`), refresh token 30 days, opaque, stored hashed, rotated on
  every use, reuse → revoke the family.
- Roles and venue access are loaded per request from memberships, never trusted from the token.
- Email code: 6 digits, hashed, 10 min, max 5 attempts, resend after 30 s, rate-limited, always
  `202`. Never log codes or tokens.
- Apple/Google tokens verified against their JWKS with `iss`/`aud`/expiry (and nonce for Apple).
- Account deletion endpoint is required (App Store).
- Auth code changes need tests for each rule above.
- Where it lives: access tokens `common/security/AccessTokenIssuer` + `JwtConfig` (keys, validation);
  public endpoints and 401/403 problems `common/security/SecurityConfig`; email sign-in
  `account/application/StartEmailSignIn` + `VerifyEmailSignIn`; Apple/Google
  `account/application/IdTokenSignIn` + `account/infra/client/IdTokenVerifier`; sessions
  `account/application/StartSession`, `RefreshTokens`, `Logout`; account deletion
  `account/application/AccountDeletion`; per-IP limits `account/application/AuthRateLimits` (an
  unparseable client address counts under one shared key, fail closed) over
  `common/ratelimit/RateLimiter`; email `common/email/EmailSender`. Values and env vars:
  ADR 0004 "Implementation notes".
- The authenticated principal's name is the user ID (`sub`); use it, never an email, to identify the
  user.

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

- Storyous client exposes **read methods only**. Gated (`architecture/ProductRules`, rule 1): no
  mutating HTTP call anywhere in `pos`. The real client's recorded-fixture tests must also fail on any
  non-GET request.
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
- Wrap the provider behind an interface (`LlmClient`, an R9 exception) so the model/provider can
  change and tests use a fake without network calls.
- Golden-question tests run against fixture data for any prompt/tool change.

## Database

- Flyway migrations are append-only. Never edit an applied migration.
- jOOQ classes are generated from the migrated schema (Testcontainers → Flyway → codegen) into
  `build/generated-src/jooq`, package `com.onemind.db.<schema>` (e.g. `com.onemind.db.pos`). `build`
  regenerates them when a migration changes; never edit generated code. A new schema must be added
  to `generateJooqClasses.schemas` in `build.gradle.kts`.
- Records stay in `infra/persistence` (R4, gated in `architecture/StructureRules`).
- Use jOOQ's DSL for all SQL, including reports (window functions, CTEs). No raw SQL strings except
  in migrations.
- Bulk sync writes: `insertInto(...).onConflict(...).doUpdate()` with batching.
- Reports read from hourly rollup tables refreshed after each sync batch; "now" values come from
  base tables. Rollups must be rebuildable from base tables.
- Snake_case table and column names. Every venue-scoped table has `venue_id` with an index.
- Money columns: `bigint` minor units + `char(3)` currency (or a fixed venue currency). Generated
  types: `timestamptz` → `Instant`, `currency` → `java.util.Currency`; build `Money` (`common/money`)
  from the two columns in the repository.
- Stock movements table is insert-only (no UPDATE/DELETE in application code).

## Testing

- Unit tests for domain logic (metrics, stock formulas, order plan, matching).
- Integration tests with Testcontainers PostgreSQL for repositories and migrations.
- API tests (`@SpringBootTest` + `WebTestClient`/`MockMvc`) for auth and tenancy.
- Storyous client tests against recorded fixtures (no live calls in CI).
- Email: `EmailSender` is concrete (R9). Tests that read sent mail register a `@Primary`
  `JavaMailSender` that records messages (a `JavaMailSenderImpl` subclass overriding
  `send(vararg SimpleMailMessage)`, see `account/AuthApiTests.RecordingMailSender`), so the real
  sender runs; no mocking library needed.

## Commands

Run from `backend/`. Docker is required for tests and jOOQ codegen (both use Testcontainers).

```bash
docker compose -f ../infra/docker-compose.yml up -d   # local PostgreSQL 18 on localhost:5433, Mailpit (SMTP 1025, UI :8025)
./gradlew bootRun                                     # run the API on :8080 (health: /actuator/health)
./gradlew test                                        # tests (start PostgreSQL via Testcontainers)
./gradlew build                                       # compile + ktlint + test + jar
./gradlew bootTestRun                                 # run the app with a Testcontainers PostgreSQL (no compose needed)
./gradlew ktlintCheck                                 # style check (also part of build)
./gradlew ktlintFormat                                # fix style violations
./gradlew generateJwtKey                              # print a new access-token signing key (JWT_SIGNING_KEY)
```

Colima: if Testcontainers' Ryuk container fails with "error while creating mount source path
…docker.sock", export `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` in your shell
profile. The Gradle daemon keeps the environment it started with: run `./gradlew --stop` after
setting it.

Spec (run from the repo root): `npx @redocly/cli@2.82.0 lint --config docs/api/redocly.yaml docs/api/openapi.yaml`.
`./gradlew openApiGenerate` / `./gradlew generateJooqClasses` regenerate the API / jOOQ code alone
(`build` does both automatically).

Not set up yet (next steps): detekt.

## Hosting ([ADR 0008](../docs/architecture/adr/0008_HETZNER_HOSTING_ZERO_COST_START.md))

- Production is one Hetzner VM with Docker Compose (Caddy, backend, PostgreSQL). Deployment,
  backup and restore scripts live in `../infra/`; never change the server by hand.
- External services sit behind adapters in `infra/client`, swapped by configuration: `FileStorage`
  (S3 API; MinIO locally) and `EmailSender` (SMTP) are concrete classes; `ReceiptExtractor` is an
  interface (ADR 0013, R9).
- Expose a health endpoint (Spring Boot Actuator `health`) for uptime monitoring; no other
  actuator endpoints public.
- Configuration only via environment variables in production.

## Definition of done (backend)

- Tests added and passing; no new warnings.
- OpenAPI/`docs/api` updated for any endpoint change.
- `docs/domain/*` updated for any rule or metric change.
- Migration included for schema changes, and it is additive (or an ADR explains why not).
