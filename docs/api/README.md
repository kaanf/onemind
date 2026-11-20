# API

> **Status:** contract tooling decided in
> [ADR 0001](../architecture/adr/0001_OPENAPI_CONTRACT_FIRST.md). The endpoint inventory below is a
> draft until it is written into `openapi.yaml`.

## Contract

- **OpenAPI 3.1** spec at `docs/api/openapi.yaml` (to be created) is the single source of truth.
- Backend generates controller interfaces + DTOs (`kotlin-spring`, `interfaceOnly`); mobile
  generates its Ktor client + DTOs (`kotlin`, `library=multiplatform`). No shared Kotlin module.
- Workflow: change the spec first, then backend and mobile in the same PR.
- Inside `/api/v1` changes are **additive only**. CI runs `oasdiff breaking` and a spec linter.
- Clients send `X-App-Version` and `X-Platform`; the backend may answer `426 Upgrade Required`.

## Cross-cutting rules from ADRs

- **Auth** ([ADR 0004](../architecture/adr/0004_OWN_JWT_AUTH.md)): access JWT 15 min, rotating
  refresh token 30 days. Email code 6 digits, 10 min, 5 attempts, resend after 30 s.
  `POST /auth/email/start` always returns `202`. `DELETE /me` deletes the account.
- **Live data** ([ADR 0005](../architecture/adr/0005_POLLING_AND_SSE.md)): live read endpoints
  return `ETag` + `dataAsOf` and support `If-None-Match` → `304`.
- **Streaming**: `POST …/assistant/messages` with `Accept: text/event-stream` streams SSE events
  `delta | block | citation | suggestedReplies | done | error`.
- **Offline counts** ([ADR 0006](../architecture/adr/0006_OFFLINE_STRATEGY.md)): count write
  endpoints require `Idempotency-Key` and accept a client `countedAt` timestamp.

## Conventions

- REST over HTTPS, JSON, base path `/api/v1`.
- Auth: `Authorization: Bearer <access token>` (short-lived JWT) + refresh token.
- Every venue-scoped route takes a `venueId` path segment. `venueId=all` means "all venues the user
  follows", for read endpoints that support aggregation.
- Money: `{ "amountMinor": 4832000, "currency": "CZK" }` (minor units, integer). The client
  formats it.
- Timestamps: ISO-8601 UTC (`2026-11-06T20:40:00Z`). Business days: `YYYY-MM-DD` local date.
  Responses that depend on local time include the venue `timeZone`.
- Periods: `period=hour|day|week|month` + optional `from`/`to` (custom range from *Choose dates*).
- Pagination: cursor-based (`cursor`, `limit`), response `{ items, nextCursor }`.
- Errors: RFC 9457 Problem Details (`type`, `title`, `status`, `detail`, `code`).
- Screen-shaped read endpoints are fine (one call per screen) for the dashboard screens, to keep
  the mobile app thin. Metrics are computed by the backend (`docs/domain/METRICS.md`).
- Freshness: read responses include `dataAsOf` (last POS sync time).

## Endpoint inventory (by screen)

### Auth and onboarding

| Method | Path | Screen | Notes |
|--------|------|--------|-------|
| POST | `/auth/email/start` | A1 | Send 6-digit code. Rate-limited. |
| POST | `/auth/email/verify` | A2 | Code → tokens. Code valid 10 min. |
| POST | `/auth/apple` | A1 | Apple identity token → tokens. |
| POST | `/auth/google` | A1 | Google ID token → tokens. |
| POST | `/auth/refresh` | – | Refresh token rotation. |
| POST | `/auth/logout` | – | |
| DELETE | `/me` | – | Account deletion (App Store requirement). |
| GET  | `/me` | – | User, memberships, role, followed venues, onboarding state. |
| POST | `/pos/storyous/connect` | A3 | Returns authorisation URL. |
| GET/POST | `/pos/storyous/callback` | A3 | OAuth callback (if applicable). |
| POST | `/pos/storyous/invite-owner` | A3 | *I'm not the owner, invite them*. |
| GET  | `/pos/sync/status` | A4 | Steps with state/progress. Poll or SSE. |
| GET  | `/venues` | A5 | Venues found on the POS account. |
| PUT  | `/me/venues` | A5 | Followed venues. |

### Dashboard

| Method | Path | Screen |
|--------|------|--------|
| GET | `/venues/{venueId}/today` | 01 Today |
| GET | `/venues/{venueId}/tables?mode=now\|week` | 01a Tables |
| GET | `/venues/{venueId}/sales?period=…` | 02 Sales |
| GET | `/venues/{venueId}/sales/products?day=…&category=…` | 02a Products by hour |
| GET | `/venues/{venueId}/sales/hourly-patterns?weeks=8` | 02b Hourly patterns |
| GET | `/venues/{venueId}/losses?period=…` | 02c Discounts & losses |
| GET | `/venues/{venueId}/staff?day=…` | 03 Staff |
| GET | `/venues/{venueId}/staff/{staffId}?day=…` | 03a Waiter detail |
| GET | `/venues/{venueId}/bills/{billId}` | Receipt (not designed) |

### Stock

| Method | Path | Screen |
|--------|------|--------|
| GET  | `/venues/{venueId}/stock/order-plan` | 04 |
| GET  | `/venues/{venueId}/stock/order-plan/lines/{itemId}` | S1 |
| PATCH| `/venues/{venueId}/stock/order-plan/lines/{itemId}` | S1 (qty, keep/skip) |
| POST | `/venues/{venueId}/stock/order-plan/send` | 04 |
| GET  | `/venues/{venueId}/stock/idle` | S2 |
| POST | `/venues/{venueId}/stock/idle/{itemId}/actions` | S2 |
| GET  | `/venues/{venueId}/stock/counts/current` | S3 |
| PUT  | `/venues/{venueId}/stock/counts/{countId}/lines/{itemId}` | S3 |
| POST | `/venues/{venueId}/stock/counts/{countId}/complete` | S3 |
| GET  | `/venues/{venueId}/stock/locations` | S4 |
| GET  | `/venues/{venueId}/stock/pull-list?date=…` | S5 |
| POST | `/venues/{venueId}/stock/transfers` | S5 |
| POST | `/venues/{venueId}/stock/invoices` (multipart image) | S6 |
| GET  | `/venues/{venueId}/stock/invoices/{invoiceId}` | S6 |
| PATCH| `/venues/{venueId}/stock/invoices/{invoiceId}/lines/{lineId}` | S6 (match) |
| POST | `/venues/{venueId}/stock/invoices/{invoiceId}/approve` | S6 (owner only) |
| GET  | `/venues/{venueId}/recipes/{productId}` | S7 |
| POST | `/venues/{venueId}/stock/corrections` | S7 |

### Assistant

| Method | Path | Screen |
|--------|------|--------|
| GET  | `/venues/{venueId}/assistant/briefing` | 05 |
| GET  | `/venues/{venueId}/assistant/messages?cursor=…` | 05 |
| POST | `/venues/{venueId}/assistant/messages` (streamed answer) | 05 |
| GET  | `/venues/{venueId}/suggestions` | 05 / Insights |
| POST | `/venues/{venueId}/suggestions/{id}/apply` · `/dismiss` | 05 |
| GET  | `/venues/{venueId}/knowledge` | 05a |
| POST | `/venues/{venueId}/knowledge/documents` (multipart) | 05a |
| PATCH| `/venues/{venueId}/knowledge/documents/{id}` (access) | 05a |
| DELETE | `/venues/{venueId}/knowledge/documents/{id}` | 05a |

### Push notifications (proposed)

| Method | Path | Notes |
|--------|------|-------|
| POST | `/me/devices` | Register FCM/APNs token. Use cases: invoice waiting for approval, stockout risk, sync error. |

## Example: `GET /venues/{venueId}/today`

```json
{
  "venueId": "v_main",
  "businessDay": "2026-11-06",
  "timeZone": "Europe/Prague",
  "dataAsOf": "2026-11-06T20:38:00Z",
  "paidRevenue": { "amountMinor": 4832000, "currency": "CZK" },
  "comparison": { "label": "last Friday", "deltaPct": 12.4 },
  "openTabs": { "total": { "amountMinor": 986000, "currency": "CZK" }, "tables": 10 },
  "tips": { "total": { "amountMinor": 394000, "currency": "CZK" }, "cardPct": 69 },
  "hourly": [
    { "hour": "16:00", "value": { "amountMinor": 120000, "currency": "CZK" }, "kind": "past" },
    { "hour": "22:00", "value": { "amountMinor": 610000, "currency": "CZK" }, "kind": "current" },
    { "hour": "23:00", "value": { "amountMinor": 560000, "currency": "CZK" }, "kind": "forecast" }
  ],
  "occupancy": { "occupied": 10, "total": 13, "seated": 41 },
  "guests": 286,
  "revenuePerGuest": { "amountMinor": 16900, "currency": "CZK" },
  "onShift": 4,
  "possibleLoss": { "amountMinor": 187000, "currency": "CZK" }
}
```
