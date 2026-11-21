# Open questions

Agents: if your task depends on one of these, **ask the user** or implement behind a seam and
reference the question ID in a code comment / PR. When a question is answered, move it to
"Resolved" with the answer and update the affected docs.

## Product

| ID | Question |
|----|----------|
| P2 | Exact permissions for `STAFF` users: which tabs can they see (Sales? Staff earnings?) and which actions can they take? |
| P3 | How do staff users get access: owner invites by email? Linked to a POS staff member? |
| P5 | Where do suggestions live in the Cross-platform design (inside Assistant feed, cards on Today, notifications)? |
| P6 | Entry points to S4 Locations, S6 Scan invoice, S7 Recipes inside the Stock tab (not designed). |
| P7 | Receipt screen, venue switcher sheet, settings, account, notifications, empty/error/loading states, dark mode: need designs. |
| P8 | Pricing / subscription and paywall? (affects onboarding) |
| P9 | "Review and send to suppliers": how is the order sent (email, PDF share, supplier portal)? |
| P10 | Supplier data (deadlines, delivery days, case sizes, prices): who enters it and where? |
| P13 | Narrow the first release to read-only dashboards (stock, receipts and counts later) to make the Storyous pitch cleaner? See `product/STORYOUS_PARTNERSHIP.md`. |
| P14 | Stock: our own system with Storyous read-only (current plan), and how to position it against Storyous Inventory. |
| P15 | Storyous partnership outcome (owner is handling it): access model, extra data (open bills, voids, attendance), terms. |
| P12 | Can the table layout be edited in the app, or only taken from the POS? |
| P16 | A venue disabled (or `hidden`, not mapped yet) in Storyous: should its owner still see its history (sales, later stock) read-only, and should *All venues* include it? Today `VenueAccess` answers 404 for a `DISABLED` venue, even for its owner (#30); relaxing it is a one-line change. Decide before phase 5. |

## Metrics

| ID | Question |
|----|----------|
| M1 | Business-day cut-off: fixed 05:00, per venue setting, or from POS opening hours? Bill assigned by close time or open time? |
| M2 | Revenue gross (incl. VAT) or net? Margins on net price? |
| M3 | Week/month comparisons: full previous period or same elapsed span? |
| M4 | Payment methods other than card/cash (vouchers, meal cards, Benefit): how to show? |
| M5 | Guest count when the POS has none: count 1 per bill, or exclude? |
| M7 | Happy Hour "extra sales" baseline method. |
| M8 | Threshold for "above usual" deleted items. |
| M9 | Stock corrections in losses at sale price or cost? |
| M10 | Order-plan forecast and safety buffer method; idle "very slow" threshold; variance threshold. |
| M11 | Unit cost: last purchase price or weighted average? |

## Integration

| ID | Question |
|----|----------|
| I1 | Storyous API auth mechanism (OAuth 2 vs API keys), rate limits, webhooks. |
| I2 | Which fields are actually available (guest count, table layout/zones, staff sign-ins, deleted items with author, Happy Hour rules)? |
| I3 | How far back can history be fetched (design assumes 90 days)? |
| I4 | Live data latency target: "synced 2 min ago" acceptable? |

## Technical

See [architecture/README.md#decisions-to-discuss](architecture/README.md#decisions-to-discuss).

## Resolved

| ID | Question | Answer | Date |
|----|----------|--------|------|
| R1 | Repo structure | Monorepo (`backend/`, `mobile/`, `docs/`) | 2026-11-20 |
| R2 | Documentation language | English | 2026-11-20 |
| R3 | Canonical design direction | Cross-platform page of the design canvas | 2026-11-20 |
| R4 | Stack | Mobile: Kotlin Multiplatform. Backend: Kotlin + Spring Boot | 2026-11-20 |
| R5 | Backend–mobile shared code | None. OpenAPI contract-first, code generated on both sides (ADR 0001) | 2026-11-20 |
| R6 | Persistence | PostgreSQL + Flyway + jOOQ, no JPA; hourly rollups for reports (ADR 0002) | 2026-11-20 |
| R7 | Mobile UI | Compose Multiplatform, shared UI (ADR 0003) | 2026-11-20 |
| R8 | Auth | Own JWT + Apple/Google token verification + email code (ADR 0004) | 2026-11-20 |
| R9 | Real-time | Foreground polling with ETag; SSE for assistant answers (ADR 0005) | 2026-11-20 |
| R10 | Offline | Read-only cache; offline stock counts via outbox (ADR 0006) | 2026-11-20 |
| R11 | Users in the first release | Owner only, no staff accounts (P2, P3 deferred) | 2026-11-20 |
| R12 | How stock comes in | Owner photographs / uploads purchase receipts and supplier invoices; AI extracts lines, owner corrects and approves | 2026-11-20 |
| R13 | Recipe import | Column-mapping screen, no AI (answers P11) | 2026-11-20 |
| R14 | First-release stock scope | Purchase receipts (S6), recipes (S7), on-demand counts (S3), user-defined locations + transfers (S4, S5). Order plan later | 2026-11-20 |
| R15 | Counts | On demand (no fixed schedule): full, per location, per area or selected items; first count = opening stock | 2026-11-20 |
| R16 | AI in the first release | Only purchase receipt extraction (S6). No assistant, chat, briefing, AI suggestions or knowledge documents; dashboard hint cards use rule-based templates | 2026-11-20 |
| R17 | Stock tab landing (first release) | S4 locations + stock value; design for it and for the location form will be added to the canvas | 2026-11-20 |
| R18 | Receipt extraction model | Claude Haiku 5.5, Sonnet 5.5 fallback (ADR 0007) | 2026-11-20 |
| R19 | Hosting | One Hetzner VM with Docker Compose, PostgreSQL self-hosted, nightly off-site backups, free tiers until launch (ADR 0008) | 2026-11-20 |
| R20 | Repository host and CI | GitHub + GitHub Actions (ADR 0008) | 2026-11-20 |
| R21 | Product name | OneMind (deep-link scheme `onemind://`) | 2026-11-20 |
| R22 | Backend architecture | Modular monolith with Spring Modulith; modules account, pos, reporting, stock; schema per module; pragmatic hexagonal; DDD-lite in stock; outbox events with idempotent listeners; db-scheduler; sealed outcomes + DomainException (ADR 0009) | 2026-11-20 |
| R24 | DB schema (platform, account, pos, reporting) | Accepted in `architecture/DB_SCHEMA.md`; user data deleted immediately, organization data after 30 days | 2026-11-20 |
| R25 | Staff attribution (M6) | Sales/guests/bills to who opened the bill, tips to who closed it | 2026-11-20 |
| R26 | Mobile architecture | crew-kmp structure, generated models in `core:api`, DataStore (Room later) (ADR 0011) | 2026-11-20 |
| R27 | Cross-cutting (ADR 0012) | Structured logs + traceId, local/prod + dev/prod variants, Czech before release, no analytics or flags in first release, CI gates, `dev` → `main` branches | 2026-11-20 |
| R23 | API design | OpenAPI 3.0.3; BFF dashboards + REST stock + command endpoints; UUIDv7 (not secrets); minor-unit money, decimal-string quantities/rates; codes + English fallback; RFC 9457; PUT + version; Idempotency-Key (ADR 0010) | 2026-11-20 |
