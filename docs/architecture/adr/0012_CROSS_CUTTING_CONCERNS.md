# 0012 · Cross-cutting concerns: logging, environments, i18n, analytics, flags, testing, branches

- **Status:** Accepted
- **Date:** 2026-11-20
- **Deciders:** Kaan

## Context

The remaining project-wide decisions before implementation. Several are already covered elsewhere
(Sentry and hosting in ADR 0008, polling/ETag in ADR 0005, error codes and localisation of
messages in ADR 0010). This ADR settles the rest, favouring zero cost and consistency with the
owner's other KMP project (crew-kmp).

## Decision

### 1. Logging and request tracing

- Backend logs are **structured JSON to stdout** using Spring Boot's built-in structured logging
  (ECS format). Verify the exact Boot 4 property names at setup.
- **Micrometer Tracing** with W3C `traceparent`; every log line carries `traceId`/`spanId`, and the
  same `traceId` is returned in RFC 9457 error responses (ADR 0010).
- The mobile app sends an `X-Request-Id` (UUID) per request and adds it to Sentry breadcrumbs
  (crew-kmp `RequestDiagnostics` pattern); the backend logs it next to `traceId`.
- Storage: Docker log rotation on the VM (size-capped). No paid log service; Grafana Loki later if
  needed.
- **Never log** emails, tokens, login codes, staff names or other personal data. Log IDs
  (`userId`, `venueId`, `receiptId`) instead. Same rule for Sentry events.

### 2. Environments and configuration

- Backend profiles: `local` (docker compose) and `prod` (Hetzner). Configuration in
  `application.yml` + environment variables; secrets only from the environment. `staging` is added
  when real users arrive (ADR 0008).
- Mobile variants: `dev` (`com.onemind.app.dev`) and `prod` (`com.onemind.app`) on Android, matching
  Xcode configurations on iOS; installable side by side.
- Per-variant values (API base URL, Sentry DSN) via **BuildKonfig** (crew-kmp convention plugin).
  The app contains no secrets (DSN and OAuth client IDs are public by design).

### 3. Internationalisation and formatting

- All user-facing text in Compose resources from day one. **English** during development;
  **Czech is added before the public release.** Plurals use CLDR categories (Czech: one, few,
  many, other); cover them with tests because plural handling has known issues.
- Number and money formatting through platform formatters behind `expect/actual` in
  `core:presentation` (iOS `NumberFormatter`, Android `NumberFormat`); no locale-aware formatting
  exists in the multiplatform Kotlin libraries.
- **Money is formatted by the venue's currency/locale, independent of the UI language**
  (`48 320 Kč` in an English UI, as in the design).
- Times are shown in the **venue's time zone** (sent by the API), never the device's.

### 4. Product analytics

- **None in the first release.** Sentry covers crashes and API errors; the pilot venues are few and
  are better understood by talking to them.
- When usage grows: PostHog EU (Frankfurt) with crew-kmp's pattern, explicit opt-in consent,
  anonymous IDs, no personal data, no session replay, no autocapture. No placeholder
  abstraction is added now.

### 5. Feature flags

- **None in the first release.** The only remote control needed is the minimum supported app
  version (`426 Upgrade Required`, ADR 0001). If flags are needed later, use PostHog flags rather
  than another service.

### 6. Testing and CI gates

| Check | Runs | Required to merge |
|-------|------|-------------------|
| Backend: build, unit + integration tests (Testcontainers), Spring Modulith boundary test, ktlint (detekt once 2.0 is stable) | every PR | yes |
| API: spec lint + `oasdiff breaking` | PRs touching `docs/api/` | yes |
| Mobile: common tests, Koin graph test, Android build | every PR | yes |
| iOS build (macOS runner) | merges to `dev` and `main`, nightly | no (quota: macOS minutes count many times against private-repo minutes; verify current GitHub pricing) |

- No coverage-percentage gate. Required tests by rule instead: a unit test per metric definition,
  a cross-venue access test per endpoint, a duplicate-delivery test per event listener, a test per
  auth rule (ADR 0004), ViewModel tests per screen.

### 7. Branch strategy (same as crew-kmp)

- `main` = released state; `dev` = integration branch.
- Work happens on short-lived branches from `dev` named with a Conventional Commit type
  (`feat/…`, `fix/…`, `chore/…`, `docs/…`); PRs target `dev`.
- Task PRs into `dev` are **squash-merged** (one commit per task). Don't reuse a branch after its
  PR is merged; start the next task from current `origin/dev`.
- Releases: one PR `dev` → `main`, merged with a **merge commit**, then tagged. Backend deploys from `main`; mobile store builds come
  from tags on `main`. Hotfix: branch from `main`, merge to `main` and back into `dev`.
- Never commit directly to `dev` or `main`. The owner approves merges and releases.

## Alternatives considered

- Paid log/observability services from day one: unnecessary cost before users.
- Server-rendered localised text: rejected in ADR 0010.
- PostHog now: low value with few pilot users; adds consent and privacy work.
- Trunk-based development on `main` with release branches: popular for mobile, but diverges from
  the crew-kmp workflow and agent rules the owner already uses.

## Consequences

- A `dev` branch is created from `main`; open work targets `dev`.
- Czech translation is a release task; every new string must exist in resources, never inline.
- iOS breakage can land on `dev` between nightly runs; acceptable given the quota.
