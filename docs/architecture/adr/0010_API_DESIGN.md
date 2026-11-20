# 0010 · API design rules

- **Status:** Accepted
- **Date:** 2026-11-20
- **Deciders:** Kaan

## Context

ADR 0001 made the API contract-first with OpenAPI. This ADR sets how the API itself is designed.
One client codebase (KMP) consumes it; the backend computes all metrics; the app polls live screens
(ADR 0005), works partly offline (ADR 0006) and must keep working while old app versions stay
installed. The rules below were checked against current practice (Stripe-style money, the IETF
Idempotency-Key draft, BFF pattern literature, PATCH/merge-patch pitfalls with code generators,
UUIDv7 trade-offs, API error localisation). The full rule set lives in `docs/api/README.md`.

## Decision

1. **OpenAPI 3.0.3**, not 3.1: openapi-generator's 3.1 support is still beta. Revisit when stable.
   (Amends ADR 0001.) Spec split by area with relative `$ref`s.
2. **Hybrid endpoint style:** screen-shaped (BFF) read endpoints for dashboards, resource-oriented
   REST for stock and account, command endpoints (`POST …/approve`) for state transitions.
3. **Multi-venue:** dashboard endpoints take `venueIds` as a query parameter; stock endpoints are
   scoped to one venue in the path.
4. **Naming:** `/api/v1`, plural kebab-case paths, camelCase JSON, `SCREAMING_SNAKE` enums; clients
   tolerate unknown fields and enum values.
5. **IDs:** UUIDv7 strings. IDs are **not secrets** (they embed creation time and are partly
   predictable); authorisation never relies on them. POS IDs are never exposed.
6. **Formats:** money as integer minor units + currency; rates and sub-minor prices as decimal
   strings; stock quantities as decimal string + unit; timestamps ISO-8601 UTC; business days as
   local dates; percentages as raw numbers. No floats for money or quantities.
7. **Text:** the API sends codes and parameters, the app renders text from its own resources.
   Errors also carry an English `detail` as a fallback for codes an older app doesn't know.
   Unknown hint-card codes are hidden.
8. **Errors:** RFC 9457 with `code`, `detail`, `params`, `traceId`, `errors[]`; `404` for resources
   in venues the user can't access.
9. **Updates:** no PATCH (merge-patch null/absent ambiguity with generated code). PUT replaces small
   resources; editable resources carry an integer `version` → `409 VERSION_CONFLICT` on mismatch.
10. **Idempotency:** `Idempotency-Key` on creating POSTs, stored 24 h, with the IETF draft's semantics
    (replay on same body; `400` missing; `422` different body; `409` in progress).
11. **Lists:** cursor pagination only for growing lists; explicit filters per endpoint.
12. **Files and async work:** multipart upload to the backend (≤ 10 MB); receipt extraction is
    asynchronous (`202` + status polling).

## Alternatives considered

- **Pure REST for dashboards:** many round trips and client-side metric logic; rejected.
- **GraphQL:** flexible queries, but adds a second contract style and tooling to a single-client app.
- **PATCH with JSON Merge Patch / JSON Patch:** smaller payloads, but null/absent ambiguity and
  generator workarounds (`JsonNullable`) with known pitfalls.
- **`ETag`/`If-Match` for concurrency:** HTTP-native, but a `version` body field is simpler with
  generated clients and visible in the model (similar to Google's AIP etag-in-resource approach).
- **Server-rendered localised text:** single source of text, but ties UI wording to backend
  releases; the app already ships string resources.
- **Integer or random v4 IDs:** integers leak record counts and growth; v4 hurts index locality.

## Consequences

- Each dashboard screen has a dedicated response schema owned by `reporting`.
- The app needs a code → string mapping for errors and hint cards, with fallbacks.
- The backend needs an idempotency-key store and `version` columns on editable tables.
