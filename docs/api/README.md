# API

Rules for the OneMind HTTP API. Decided in
[ADR 0001](../architecture/adr/0001_OPENAPI_CONTRACT_FIRST.md) (contract-first) and
[ADR 0010](../architecture/adr/0010_API_DESIGN.md) (design rules). The endpoint inventory at the end
is a plan; the source of truth for shapes is the OpenAPI spec once written.

## Contract

- **OpenAPI 3.0.3** spec `docs/api/openapi.yaml` is the single source of truth.
  (3.1 support in openapi-generator is still beta; revisit when stable.)
- Layout, split by area and joined with relative `$ref`s (no bundling step):

  ```
  docs/api/
  ├── openapi.yaml                      ← entry: info, servers, security, path list
  ├── redocly.yaml                      ← lint config
  ├── lint/                             ← lint plugin, money word list, fixture CI expects to fail
  ├── oasdiff-levels.txt                ← breaking-check severity overrides
  ├── paths/{account,dashboard,stock}/*.yaml   ← added with the first endpoints
  └── components/{schemas,responses,parameters}/*.yaml
  ```
- Shared components so far: `Problem`, `FieldError`, `Money`; parameters `IdempotencyKey`,
  `AppVersion`, `Platform`, `VenueId`, `VenueIds`; problem responses `BadRequest`, `Unauthorized`,
  `Forbidden`, `NotFound`, `Conflict`, `UnprocessableEntity`, `UpgradeRequired`, `TooManyRequests`.
  Reuse them with `$ref` instead of redefining.
- Lint: `npx @redocly/cli@2.82.0 lint --config docs/api/redocly.yaml docs/api/openapi.yaml` (repo root).
- Backend generates controller interfaces + DTOs (`kotlin-spring`, `interfaceOnly`); mobile generates
  models only (`kotlin`, `multiplatform`, kotlinx.serialization; ADR 0011). No shared Kotlin module.
- openapi-generator's parser does not resolve a `$ref` from one split schema file to another
  (`Problem.yaml` → `./FieldError.yaml` falls back to `object`). Mobile bundles the spec with Redocly
  before generating; the backend's generated `Problem.errors` is still `List<Any>` (unused: errors are
  built with Spring's `ProblemDetail`).
- Workflow: change the spec first, then backend and mobile in the same PR.
- Inside `/api/v1` changes are **additive only**. CI (`.github/workflows/ci.yml`, PRs touching
  `docs/api/`) runs the linter and `oasdiff breaking` against `main`'s spec (skipped while `main` has
  none). It fails on errors and warnings; `oasdiff-levels.txt` allows new response enum values
  (allowed in v1) and fails on removed ones. Locally: `oasdiff breaking --fail-on WARN --severity-levels
  docs/api/oasdiff-levels.txt <main-copy>/docs/api/openapi.yaml docs/api/openapi.yaml`.
- Clients send `X-App-Version` and `X-Platform`; the backend may answer `426 Upgrade Required`
  (`APP_UPGRADE_REQUIRED`, `params.platform`, `params.minimumVersion`). The minimum per platform comes
  from `MIN_APP_VERSION_IOS` / `MIN_APP_VERSION_ANDROID` (empty = no check). Every `/api/v1` request is
  checked before authentication (so an outdated app is told to update even on sign-in and refresh);
  `/actuator/health` never is. Without both headers, or for another platform, a request passes (not
  an app). A version that doesn't parse is `400 APP_VERSION_INVALID` while that platform has a
  minimum. Versions compare by their numeric parts (`1.10 > 1.9`, `1.4 = 1.4.0`); a `-beta`/`+build`
  suffix is ignored, so raise the minimum to a released version.

## Endpoint style

| Area | Style | Example |
|------|-------|---------|
| Dashboard (Today, Sales, Staff, Tables, …) | **Screen-shaped** (BFF): one call per screen, computed by the backend | `GET /dashboard/today?venueIds=…` |
| Stock, account | **Resource-oriented** REST | `GET /venues/{venueId}/locations` |
| State transitions | **Command endpoints** (POST to a sub-resource) | `POST /venues/{venueId}/receipts/{id}/approve` |

- Dashboard endpoints take `venueIds` (one or more) as a query parameter, so one venue, several or
  all followed venues use the same endpoint. Stock is always one venue: `/venues/{venueId}/…`.
- The app never computes metrics; it formats what the backend returns (`docs/domain/METRICS.md`).

## Naming

- Base path `/api/v1`. Plural nouns, kebab-case paths: `/venues/{venueId}/stock-counts`.
- JSON fields camelCase. Enum values `SCREAMING_SNAKE` (`"status": "NEEDS_MATCH"`).
- State changes are command endpoints (`/approve`, `/complete`, `/confirm`), not field updates.
- Clients must tolerate unknown fields and unknown enum values (map to `UNKNOWN`).

## Identifiers

- All IDs are **UUIDv7**, as strings in the API, typed in code (`VenueId`, `ReceiptId`).
  PostgreSQL 18 `uuidv7()` or app-side generation.
- **IDs are not secrets.** They embed a creation timestamp and are partly predictable. Never rely on
  an ID being unguessable: every request is authorised by venue access. Secrets (refresh tokens,
  email codes) are separate, cryptographically random values.
- POS (Storyous) IDs never appear in the API.

## Data formats

| Type | Format | Example |
|------|--------|---------|
| Money amount | integer minor units + currency | `{"amountMinor": 4832000, "currency": "CZK"}` |
| Rate / sub-minor price (cost per ml, tax rate) | decimal string | `"0.6000"` |
| Stock quantity | decimal string + unit | `{"value": "1.25", "unit": "BOTTLE"}` |
| Timestamp | ISO-8601 UTC | `"2026-11-06T20:40:00Z"` |
| Business day | local date | `"2026-11-06"` |
| Percentages, deltas | raw number, app formats | `12.4` |

Never send money or quantities as JSON floats. The lint rule `onemind/money-minor-units`
(`lint/money-plugin.js`) checks money-named properties and components (`Price`), their array items
and map values (`additionalProperties`), also across `$ref`'d files, by whole words from
`lint/money-words.json`; a money name that isn't money ends in `Rate`, `Ratio`, `Percent`, `Pct`,
`Share`, `Count` or `Quantity`. Responses that depend on local time include the venue `timeZone`.

## Text and localisation

- The API does not send UI sentences. It sends **codes and parameters**; the app builds the text
  from its own string resources (English, Czech later).
- Rule-based hint cards: `{"code": "BEST_TABLE_ZONE", "params": {...}}`. Unknown code → the app hides
  the card.
- Errors: see below; `detail` is an English fallback for codes the app doesn't know yet (older app
  versions after a backend release).

## Errors

RFC 9457 Problem Details with extension members:

```json
{
  "type": "https://api.onemind.app/problems/receipt-already-approved",
  "title": "Receipt already approved",
  "status": 409,
  "code": "RECEIPT_ALREADY_APPROVED",
  "detail": "This receipt was approved on 17 Nov and can't be approved again.",
  "params": { "approvedAt": "2026-11-17T14:05:00Z" },
  "traceId": "4bf92f3577b34da6",
  "errors": [{ "field": "lines[2].quantity", "code": "POSITIVE" }]
}
```

- `code` is stable and machine-readable; the app switches on it, never on `title`/`detail`.
- `type` is `https://api.onemind.app/problems/` + the code in kebab case; `title` is the code as a
  sentence (`RECEIPT_ALREADY_APPROVED` → `Receipt already approved`).
- `errors[]` lists field validation problems (`400` with code `VALIDATION_FAILED`). A field's `code`
  is the Bean Validation constraint in `SCREAMING_SNAKE` (`@NotBlank` → `NOT_BLANK`, `@Positive` →
  `POSITIVE`).
- Codes that are not specific to a feature: `VALIDATION_FAILED`, `VERSION_CONFLICT`,
  `INTERNAL_ERROR` (`500`, never with internal details) and the HTTP status name for requests that
  fail before reaching a feature (`BAD_REQUEST` for malformed JSON or a missing parameter,
  `NOT_FOUND` for an unknown path, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`, …).
- Status codes: `400` malformed, `401` no/invalid token, `403` the user's role in the venue is too
  low (`VENUE_ROLE_REQUIRED`, `params.role`), `404` not found (also a venue the user isn't a member
  of or that is disabled, `VENUE_NOT_FOUND`, and resources in it, so they're not revealed),
  `409` conflict (version, state), `422` business rule violation, `426` app too old,
  `429` rate limited.

## Updates, concurrency, idempotency

- **No PATCH.** Small editable resources (location, stock item, recipe) are replaced with **PUT**
  (full representation). State transitions use command endpoints.
- **Optimistic locking:** editable resources carry an integer `version`. PUT and commands send it;
  if it doesn't match → `409` with code `VERSION_CONFLICT`.
- **Idempotency:** POSTs that create things (receipt upload, transfers, count lines, commands with
  side effects) require an `Idempotency-Key` header (UUID), stored 24 h. Following the IETF draft:
  - same key + same body → the original response is replayed,
  - missing key where required → `400`,
  - same key + different body → `422`,
  - same key while the first request is still running → `409`.
  - Codes: `INVALID_IDEMPOTENCY_KEY` (`400`, not a UUID), `IDEMPOTENCY_KEY_REUSED` (`422`),
    `IDEMPOTENT_REQUEST_IN_PROGRESS` (`409`); a missing required key is `BAD_REQUEST`.
  - Keys are scoped per user. A replay repeats status, body, `Content-Type` and `Location`.
    Responses with `5xx` are not stored, so the client can retry with the same key.
  - An operation requires the key by declaring the shared `IdempotencyKey` header parameter in the
    spec; the backend then applies these rules to it. On other operations the header is ignored.

## Lists, filters, files, long-running work

- **Pagination:** only for lists that grow (receipts, stock movements). Cursor-based:
  `?cursor=…&limit=…` → `{ "items": [...], "nextCursor": "…" | null }`. Small lists (locations)
  are not paginated.
- **Filters:** explicit per endpoint (`period`, `from`, `to`, `category`, `locationId`). No generic
  filter language.
- **Uploads:** receipts are sent as `multipart/form-data` to the backend, max 10 MB; the app
  compresses photos first.
- **Async work:** receipt extraction returns `202 Accepted` with the receipt in status `EXTRACTING`;
  the app polls `GET` until `NEEDS_REVIEW`. Then the owner reviews and approves.

## Auth and headers

- `Authorization: Bearer <access JWT>` (15 min); refresh token rotation via `/auth/refresh`
  ([ADR 0004](../architecture/adr/0004_OWN_JWT_AUTH.md)).
- Email code start always answers `202`. `POST /auth/logout` (refresh token in the body) always
  answers `204`. `DELETE /me` deletes the account immediately (`204`).
- Auth codes: `EMAIL_CODE_RESEND_TOO_SOON` and `RATE_LIMITED` (`429`, with `Retry-After` and
  `params.retryAfterSeconds`), `EMAIL_CODE_INVALID` (`params.attemptsLeft`), `EMAIL_CODE_EXPIRED`,
  `EMAIL_CODE_ATTEMPTS_EXCEEDED` (`422`), `REFRESH_TOKEN_INVALID` (`401`: sign in again). Apple/Google:
  `ID_TOKEN_INVALID` (`401`: signature, issuer, audience, expiry or nonce), `EMAIL_NOT_VERIFIED`
  (`422`: a new Apple/Google account without a verified email; use an email code). A missing or
  invalid access token is `401 UNAUTHORIZED` with `WWW-Authenticate: Bearer`; `403 FORBIDDEN` is
  only Spring Security denying a request. Venue access is `404 VENUE_NOT_FOUND` (not a member, unknown
  or disabled venue) or `403 VENUE_ROLE_REQUIRED` (role too low).
- Live read endpoints return `ETag` and `dataAsOf`; clients send `If-None-Match` → `304`
  ([ADR 0005](../architecture/adr/0005_POLLING_AND_SSE.md)).
- Count line writes accept a client `countedAt` and require `Idempotency-Key`
  ([ADR 0006](../architecture/adr/0006_OFFLINE_STRATEGY.md)).

## Endpoint inventory (first release, planned)

### Auth and onboarding

| Method | Path | Screen |
|--------|------|--------|
| POST | `/auth/email/start` | A1 |
| POST | `/auth/email/verify` | A2 |
| POST | `/auth/apple` · `/auth/google` | A1 |
| POST | `/auth/refresh` · `/auth/logout` | – |
| GET / DELETE | `/me` | – |
| POST | `/pos/storyous/connect` | A3 |
| GET | `/pos/sync/status` | A4 |
| GET | `/venues` | A5 |
| PUT | `/me/followed-venues` | A5 |

### Dashboard (screen-shaped, `?venueIds=…`)

| Method | Path | Screen |
|--------|------|--------|
| GET | `/dashboard/today` | 01 |
| GET | `/dashboard/tables?mode=now\|week` | 01a |
| GET | `/dashboard/sales?period=…` | 02 |
| GET | `/dashboard/products?day=…&category=…` | 02a |
| GET | `/dashboard/hourly-patterns?weeks=8` | 02b |
| GET | `/dashboard/losses?period=…` | 02c |
| GET | `/dashboard/staff?day=…` | 03 |
| GET | `/dashboard/staff/{staffId}?day=…` | 03a |

### Stock (`/venues/{venueId}/…`)

| Method | Path | Screen |
|--------|------|--------|
| GET / POST | `/locations` | S4 |
| PUT | `/locations/{id}` | location form |
| POST | `/locations/{id}/archive` | location form |
| GET / POST | `/stock-items` | S6, S7 |
| PUT | `/stock-items/{id}` | – |
| GET | `/stock?locationId=…` | S4, stock tab |
| GET / POST (multipart) | `/receipts` | S6 |
| GET | `/receipts/{id}` | S6 |
| PUT | `/receipts/{id}/lines` | S6 (corrections, matches) |
| POST | `/receipts/{id}/approve` | S6 |
| GET / POST | `/stock-counts` | S3 |
| PUT | `/stock-counts/{id}/lines/{stockItemId}` | S3 |
| POST | `/stock-counts/{id}/complete` | S3 |
| GET | `/pull-list?date=…` | S5 |
| POST | `/transfers` | S5 |
| GET / POST | `/recipes` | S7 |
| GET / PUT | `/recipes/{productId}` | S7 |
| POST | `/recipes/import` (multipart Excel) + `/recipes/import/{id}/confirm` | S7 |

Later (not first release): order plan, idle stock, corrections, assistant, knowledge, push devices.
