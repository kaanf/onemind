# Roadmap

Where the project is and what comes next. Agents: read this after `AGENTS.md` at the start of a
session, pick the first unchecked item of the current phase (unless the owner says otherwise), and
tick items here in the same PR that completes them. When several sessions run in parallel, an
orchestrator assigns the items and is the only one who ticks them here: see
[PARALLEL_WORK.md](PARALLEL_WORK.md).

Last updated: 2026-11-21.

## Status

- **Phase 0 is done.** Product/design docs, ADRs 0001–0012, DB schema (`platform`, `account`,
  `pos`, `reporting`), backend scaffold, local Docker (Colima) are in `dev`.
- **Phase 1 is done.** Error handling, JSON logging + tracing, idempotency, event registry +
  db-scheduler and CI (backend build/test, spec lint, `oasdiff breaking`) are in `dev`.
- **Current phase: 2 — Mobile scaffold.** Phase 3 backend auth is done in parallel (backend lane):
  email code, JWT + refresh rotation (#22), Apple/Google (#27), logout and `DELETE /me` (#28),
  auth rule tests + `VenueAccess` cross-venue check (#30). Next: app shell (mobile).
- **Blockers outside the code:** Storyous partnership / API credentials (P15, owner); design
  additions for the first-release Stock tab landing and location form (owner, only needed in
  phase 6); update the design canvas to the two darkened colour tokens (`textSecondary`,
  `tablesRamp` step 5) from `docs/design/DESIGN_SYSTEM.md` (owner/designer).

## Phase 0 — Foundations ✅

- [x] Agent docs: `AGENTS.md`, `docs/` (product, domain, API, design, integrations, architecture)
- [x] Architecture decisions: ADR 0001–0012
- [x] DB schema for `platform`, `account`, `pos`, `reporting` (`docs/architecture/DB_SCHEMA.md`)
- [x] Storyous API verified; partnership research parked (`docs/product/STORYOUS_PARTNERSHIP.md`)
- [x] Backend scaffold (Spring Boot 4.2, Java 25), `infra/docker-compose.yml` (PostgreSQL 18 on 5433)
- [x] GitHub repo, `dev`/`main` branches, Docker via Colima

## Phase 1 — Backend infrastructure ✅

- [x] Version catalog (`gradle/libs.versions.toml`), ktlint (detekt deferred until 2.0 is stable)
- [x] OpenAPI spec skeleton (`docs/api/openapi.yaml`, split files, 3.0.3) + `kotlin-spring` codegen
      (`interfaceOnly`, Boot 4 / Jackson 3) + spec lint
- [x] Flyway migrations from `DB_SCHEMA.md`: `account`, `pos`, `reporting` (`platform` tables come with
      their own items below)
- [x] jOOQ codegen from the migrated schema (Testcontainers → Flyway → jOOQ), converters for
      `Money`/`Instant` in `common`
- [x] Module packages `account`, `pos`, `reporting`, `stock`, `common` + Spring Modulith boundary
      test (incl. jOOQ package rules)
- [x] Spring Modulith event publication registry (JDBC, `platform` schema, purge job) + db-scheduler
- [x] Error handling: `DomainException` hierarchy + RFC 9457 `@ControllerAdvice` (`code`, `detail`,
      `params`, `traceId`)
- [x] Structured JSON logging + Micrometer Tracing (`traceId`, `X-Request-Id`)
- [x] Idempotency-Key filter + `platform.idempotency_record`
- [x] GitHub Actions: backend build/test, spec lint + `oasdiff breaking`

## Phase 2 — Mobile scaffold ⏭️

- [x] Start from crew-kmp `build-logic` + version catalog (bump to current stable)
- [x] Modules per ADR 0011: `core:{api,data,domain,presentation,designsystem}`,
      `feature:{auth,today,sales,staff}`, `composeApp`, `androidApp`, `iosApp`
- [x] `core:api` generation from `openapi.yaml` (models only)
- [x] Design system: tokens, Inter / Inter Tight, components from `docs/design/DESIGN_SYSTEM.md`
      (light palette only; dark palette waits for P7)
- [ ] App shell: navigation root, 4-tab bar (Today, Sales, Staff, Stock later), Koin + graph test
- [ ] Variants `dev`/`prod` with BuildKonfig; Sentry in hosts
- [x] GitHub Actions: common tests + Android build on PR; iOS build on `dev`/`main` + nightly
      (nightly/manual iOS runs start once `main` has the workflow, i.e. after the first release)

## Phase 3 — Auth (first vertical slice)

- [x] Backend: email code (start/verify), Apple, Google, JWT access + rotating refresh, logout,
      `DELETE /me` (ADR 0004)
- [ ] Mobile: A1 sign in, A2 email code, secure token storage, refresh on 401, update screen on 426
- [x] Tests for every auth rule; cross-venue access test helper
- [x] Security `AuthenticationEntryPoint` / `AccessDeniedHandler` answer `401`/`403` as RFC 9457
      problems through the `handlerExceptionResolver` (until then Spring Security's own responses)
- [x] The authenticated principal's name is the user's UUID (JWT `sub`): `IdempotencyFilter` scopes
      keys by it and skips requests without one

## Phase 4 — Storyous integration

- [ ] `PosProvider` + read-only Storyous client (client credentials, token cache, paging)
- [ ] Sync jobs (db-scheduler): places → venues, menu, desks, bills (`modifiedSince`), initial
      90-day import, `BillsSynced` event
- [ ] Reporting rollups refreshed from `BillsSynced`; rebuild job
- [ ] Onboarding A3–A5 (adapted: no OAuth redirect; see `docs/integrations/STORYOUS.md`)
- [ ] Until credentials exist: build and test against recorded fixtures
- [ ] Purge job: delete organizations whose `deletion_due_at` has passed (set by `DELETE /me`, #28)

## Phase 5 — Dashboards

- [ ] Today (01) and Tables "This week" (01a)
- [ ] Sales (02), Products by hour (02a), Hourly patterns (02b), Discounts & losses (02c)
- [ ] Staff (03), Waiter detail (03a)
- [ ] Rule-based hint cards (codes + params), venue switcher / all-venues view
- [ ] Polling + ETag, offline read cache (DataStore)

## Phase 6 — Stock (depends on P13/P14)

- [ ] Decide P13 (dashboards-only first release?) and P14 (stock positioning)
- [ ] `stock` schema + migrations
- [ ] Purchase receipts: upload, ISDOC parsing, Claude Haiku 5.5 extraction, review, approve (ADR 0007)
- [ ] Locations + transfers, on-demand counts (opening count), recipes (column-mapping import)
- [ ] Design system components for stock screens: Stepper, FractionPicker, ChecklistRow

## Phase 7 — Release preparation

- [ ] Czech translation
- [ ] Hetzner VM: Caddy, backend, PostgreSQL, nightly off-site backups + restore test (ADR 0008)
- [ ] Production Sentry, uptime monitor, transactional email provider
- [ ] Prod config: set `APPLE_CLIENT_IDS` / `GOOGLE_CLIENT_IDS` (empty = provider off)
- [ ] Apple token revocation on account deletion (`/auth/revoke`): app sends `authorizationCode`,
      store the Apple refresh token per identity, `.p8` client-secret key; required before App Store
      review (deferred by the owner; `docs/architecture/README.md` item 12)
- [ ] Privacy policy, terms, GDPR review (account deletion, data retention)
- [ ] App Store / Play listings, first release `dev` → `main` + tag

## Later

- detekt (`dev.detekt`) once 2.0 is stable; drop the ktlint Kotlin-compiler pin in
  `backend/build.gradle.kts` once ktlint supports the project's Kotlin version.


Assistant (chat, briefing, suggestions, knowledge; `ChatBubble`/`Composer` components), order plan, idle stock, corrections, staff
shifts via a staff app, multi-POS (Dotykačka first), product analytics (PostHog EU).
