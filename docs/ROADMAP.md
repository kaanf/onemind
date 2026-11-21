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
  auth rule tests + `VenueAccess` cross-venue check (#30), 426 min-version check (#36). App shell is
  done (#32). ADR 0013 audit (#40, #41), ArchUnit gates (#42), product-rule gates (#43) and the
  Konsist guard module (#39) are in `dev`. Phase 4 runs on mock POS data (owner, 2026-11-21).
- **Blockers outside the code:** Storyous partnership / API credentials (P15, owner; not needed
  until the real client is connected); design
  additions for the first-release Stock tab landing and location form (owner, only needed in
  phase 6); update the design canvas to the two darkened colour tokens (`textSecondary`,
  `tablesRamp` step 5) from `docs/design/DESIGN_SYSTEM.md` (owner/designer).

## Process and guardrails (from the 2026-11-21 setup review; runs alongside the phases)

Every gate counts as done only when its PR links a red CI run from a deliberate violation and the
green run after it.

- [x] Merge safety and coordination: orchestrator verifies checks itself, PLAN message, `critical
      areas` line, guard files, autonomy tier, batched owner questions, public-repo rule (#35)
- [x] Fork PR workflows need approval for all outside contributors (applied 2026-11-21)
- [x] `CI result` aggregator job (fails on failure, cancel or unexpected skip); iOS framework link job
      on mobile PRs feeds it (#33)
- [x] Rulesets: `dev` PR + `CI result` + squash only; `main` PR + `CI result` + merge commit only; no
      force push or deletion (applied 2026-11-21)
- [ ] Label PRs touching guard files `guard-files` and comment the list (ci)
- [ ] `permissions.deny` snippets for each lane's `.claude/settings.local.json` (orchestrator)
- [ ] detekt 2.0 as a non-blocking report job with baseline, backend and mobile; one PR, half a day;
      dropped if it needs a Kotlin pin (ci)
- [ ] "Version notes" in `backend/AGENTS.md` and `mobile/AGENTS.md`, each line linked to an official
      release note or migration guide (backend, mobile)
- [ ] Note for the owner: app-level venue scoping + test helper vs PostgreSQL row-level security
      (orchestrator; RLS would need an ADR)

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
- [x] App shell: navigation root, 4-tab bar (Today, Sales, Staff, Stock later), Koin + graph test (#32)
- [ ] Variants `dev`/`prod` with BuildKonfig; Sentry in hosts
- [x] GitHub Actions: common tests + Android build on PR; iOS build on `dev`/`main` + nightly
      (nightly/manual iOS runs start once `main` has the workflow, i.e. after the first release)
- [x] `:core:konsist` guard module: ADR 0011 module rules, no `Float`/`Double` money, one clock seam
      (Konsist 0.18.1). No mobile feature screen starts before this merges. (#39)
- [ ] Phase-end `REVIEW pr: audit`

## Phase 3 — Auth (first vertical slice)

- [x] Backend: email code (start/verify), Apple, Google, JWT access + rotating refresh, logout,
      `DELETE /me` (ADR 0004)
- [ ] Mobile: A1 sign in, A2 email code, secure token storage, refresh on 401, update screen on 426
- [x] Backend: minimum app version (`X-App-Version` / `X-Platform`) → `426 Upgrade Required` (#36)
- [x] ADR 0013 "Backend module internal structure" (replaces ADR 0009) (#34)
- [x] Audit existing backend code against ADR 0013 and fix deviations (before the gates, no baseline)
      (#40, #41)
- [x] ArchUnit gates for ADR 0013 rules + Modulith `allowedDependencies`; `backend/AGENTS.md` names
      the reference module (#42)
- [x] Product-rule gates: no `Float`/`Double` money (backend + spec lint), single `BusinessDay` entry
      point with midnight and Europe/Prague DST tests, POS client read-only (fixtures fail on non-GET)
      (#43)
- [ ] Gate follow-ups from the #39/#43 reviews: mobile Konsist reads `docs/api/lint/money-words.json`
      instead of a copy, one plural rule for not-money suffixes in all three gates; spec lint survives
      self-referencing schemas; `billNumber`-style ids not flagged; unreflectable classes fail the rule;
      `backend/AGENTS.md` names the hour helper in `BusinessDayCalendar`; `METRICS.md` says rollup
      business dates come from `BusinessDayCalendar`, not SQL date functions
- [ ] End-to-end auth smoke test: real backend (Testcontainers) driven by a client generated from
      `openapi.yaml`: email code, refresh rotation, 401 refresh, 426
- [ ] Phase-end `REVIEW pr: audit`
- [x] Tests for every auth rule; cross-venue access test helper
- [x] Security `AuthenticationEntryPoint` / `AccessDeniedHandler` answer `401`/`403` as RFC 9457
      problems through the `handlerExceptionResolver` (until then Spring Security's own responses)
- [x] The authenticated principal's name is the user's UUID (JWT `sub`): `IdempotencyFilter` scopes
      keys by it and skips requests without one

## Phase 4 — POS data (mock first)

Owner decision 2026-11-21: the real Storyous API is not connected for now; everything runs on mock
data behind `PosProvider`. Mock data is dev/demo only: never shown as a real venue's figures (rule 2).

- [ ] `PosProvider` + mock provider: deterministic, realistic venues, menu, desks, staff and bills
      (bills across midnight, Europe/Prague DST days, discounts, voids, open tabs), shaped like the
      Storyous API in `docs/integrations/STORYOUS.md`. Acceptance (rule 2): enabled only in the
      local/dev profile; prod startup fails if it is configured (test); mock venues are visibly
      named as demo; no fixture data is packaged into the prod artifact
- [ ] Interim onboarding: after verify, a new account picks mock places on A5 (dev/demo only), which
      creates the organization, venues and membership; update `docs/product/SCREENS.md`
- [ ] Sync jobs (db-scheduler) fed by `PosProvider`: places → venues, menu, desks, bills
      (`modifiedSince`), initial 90-day import, `BillsSynced` event
- [ ] Reporting rollups refreshed from `BillsSynced`; rebuild job
- [ ] Later (P15): read-only Storyous client behind `PosProvider` (client credentials, token cache,
      paging), built and tested against recorded fixtures that fail on any non-GET call; onboarding
      A3–A5 (no OAuth redirect; see `docs/integrations/STORYOUS.md`)
- [ ] Purge job: delete organizations whose `deletion_due_at` has passed (set by `DELETE /me`, #28)
- [ ] Phase-end `REVIEW pr: audit`

## Phase 5 — Dashboards

- [ ] Today (01) and Tables "This week" (01a)
- [ ] Sales (02), Products by hour (02a), Hourly patterns (02b), Discounts & losses (02c)
- [ ] Staff (03), Waiter detail (03a)
- [ ] Rule-based hint cards (codes + params), venue switcher / all-venues view
- [ ] Polling + ETag, offline read cache (DataStore)
- [ ] Phase-end `REVIEW pr: audit`

## Phase 6 — Stock (depends on P13/P14)

- [ ] Decide P13 (dashboards-only first release?) and P14 (stock positioning)
- [ ] `stock` schema + migrations; acceptance: the app DB role cannot DELETE/UPDATE stock history
      tables (REVOKE or trigger), proven by a test
- [ ] Purchase receipts: upload, ISDOC parsing, Claude Haiku 5.5 extraction, review, approve (ADR 0007)
- [ ] Locations + transfers, on-demand counts (opening count), recipes (column-mapping import)
- [ ] Design system components for stock screens: Stepper, FractionPicker, ChecklistRow
- [ ] Phase-end `REVIEW pr: audit`

## Phase 7 — Release preparation

- [ ] Precondition: the real Storyous client (phase 4 "Later", P15) is connected; no release on mock data
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

- detekt as a blocking check once 2.0 is stable (a non-blocking job is tried above); drop the ktlint Kotlin-compiler pin in
  `backend/build.gradle.kts` once ktlint supports the project's Kotlin version.


Assistant (chat, briefing, suggestions, knowledge; `ChatBubble`/`Composer` components), order plan, idle stock, corrections, staff
shifts via a staff app, multi-POS (Dotykačka first), product analytics (PostHog EU).
