# Storyous POS integration

Verified against the public docs at https://docs.api.storyous.com/ on 2026-11-20 (read from the
docs' OpenAPI pages). Anything marked **undocumented** must be confirmed with Storyous.

## Rules

- **Read-only.** We only call read endpoints, never create/update/delete anything in Storyous
  (the API does offer write endpoints, e.g. stock ups; we don't use them). The Storyous client
  exposes only read methods; a test fails if a non-GET call is added.
- Credentials (client ID/secret, tokens) are stored encrypted, server-side only.
- All Storyous access goes through one backend module behind a `PosProvider` interface.
- Until P15 is settled the only `PosProvider` is a mock provider (owner, 2026-11-21; ADR 0013);
  the client below is built later behind the same interface.

## Authentication (verified)

- OAuth2 **client credentials**: `POST https://login.storyous.com/api/auth/authorize`,
  form body `client_id`, `client_secret`, `grant_type=client_credentials` →
  `{ token_type: "Bearer", access_token, expires_at }`.
- "The token has access to all merchants **assigned to your app**." There is **no
  authorization-code flow** where an owner signs in and approves us. How a merchant gets assigned to
  our app is **undocumented** (partner/onboarding process with Storyous).
- The auth endpoint is rate-limited: cache the token until `expires_at`.
- API base URL: `https://api.storyous.com`. Errors: `{ code, message }`.

> **Impact:** onboarding screen A3 (*Continue to Storyous* → approve → come back) can't work as
> designed. See open question I1.

## Webhooks / DataSync (verified: not available)

- DataSync (webhook push of changed data per domain) is **deprecated and not available for new
  activations**. We must **poll** with `modifiedSince`.

## Endpoints we can use

| Endpoint | Gives us |
|----------|----------|
| `GET /merchants/{merchantId}` | Merchant (name, businessId, vatId, isVatPayer, countryCode, currencyCode) and its **places** (placeId, name, state `active/disabled/hidden`, address parts with lat/long). No time zone, no opening hours. |
| `GET /bills/{merchantId}-{placeId}` | **Paid** bills. Filters: `from`, `till`, `modifiedSince` (then ordered by `_lastModifiedAt`), `limit`, `lastBillId` (cursor), `refunded`, `includeDeleted`, `hasLoyalty`, `reservationId`. Returns `nextPage`. |
| `GET /bills/{merchantId}-{placeId}/{billId}` | Bill detail incl. **items**. |
| `GET /deskViews/{merchantId}-{placeId}` (+ `/desks`, `/sections`) | Sections (name, color) and desks (deskId, name, code, color, vatRate, default price level / discount, `_virtual`, `_removed`). |
| `GET /menu/...` (menu tree) | Categories and products: productId, name, posName, measure, ean, price levels/prices, VAT rates, per-place values, enabled. |
| `GET /menu/{merchantId}/timeBased/{placeId}` | Time-based menus, "e.g. daily-menu, **Happy-Hours**". |
| `GET /stocks/{merchantId}/...` | Storyous's **own stock module**: stocks (warehouses, `isCentral`, placeId), items (amount, critical/optimal amount, prices, measure conversions), stock ups (deliveries with supplier and lines), stock takings, suppliers. |

### Bill fields (verified)

`billId` (bill number, e.g. `KB2018007144`), `sessionCreated` (tab opened), `createdAt`, `paidAt`,
`fiscalizedAt`, `finalPrice`, `finalPriceWithoutTax`, `taxes[]`, `discount`, `rounding`, `tips`
(bill total), `currencyCode`, `refunded`, `deleted`, `paymentMethod`, `payments[] {paymentMethod,
priceWithVat}`, `createdBy` / `paidBy` `{personId, fullName, userName}`, **`personCount`** (guests),
`deskId`, `customerId`, loyalty and order-provider fields.

Items: `name`, `amount`, `measure`, `price`, `vatRate`, `productId`, `categoryId`, additions,
`ean`, `discounts[] {originalPrice, amountType, percentValue, absoluteValue, discountType, note}`.

Money values in bills are **decimal strings** (`"10.00"`); timestamps carry an offset
(`2018-04-20T14:49:03+02:00`).

## What the design needs vs. what the API gives

| Need (screens) | Status |
|----------------|--------|
| Venues (A4, A5, switcher) | ✅ places of a merchant. Discovering which merchants are assigned to us: **undocumented**. |
| Paid revenue, avg bill, card/cash split (01, 02) | ✅ bills + payments |
| Guests, per guest (01, 02, 02b) | ✅ `personCount` |
| Tips (01, 02) | ✅ bill total; card vs cash share only by inferring from payments |
| Products sold, by hour (02a) | ✅ items, but **no per-item order time**: hour must come from the bill (`sessionCreated` / `createdAt` / `paidAt`) |
| Discounts (02c) | ✅ per-item `discounts` with `discountType` |
| Refunds (02c) | ✅ `refunded` bills |
| Happy Hour rule (02c) | 🟡 time-based menus include Happy Hours (shape to check) |
| **Deleted items (storno) per waiter** (02c, 03a, 01) | ❌ only whole deleted bills (`includeDeleted`), no item-level deletions or who deleted |
| **Bills closed without payment** (02c) | ❓ undocumented |
| **Open tabs, occupancy now, seated now** (01, 01a) | ❌ only paid bills are available |
| Tables: names, sections/zones (01a) | ✅ desks + sections |
| **Table seats, floor layout** (01a) | ❌ no seats or positions |
| Revenue per table (01a, week) | ✅ via `deskId` on bills |
| Staff: sales/guests/bills per person (03, 03a) | ✅ via `createdBy` / `paidBy` |
| **Staff roles, sign-ins, shifts, hours, on shift** (03, 01) | ❌ no endpoint (persons only via deprecated DataSync: id + name) |
| Product cost (margins) | ❌ bill costs only in deprecated DataSync |
| Stock (S3–S7) | 🟡 Storyous has its own stock module (and a `stockUpParsing` feature) — overlaps our stock features |
| Push updates | ❌ polling only |
| History limit, rate limits | ❓ undocumented |

## Mock provider (dev/demo only)

Until P15 the only `PosProvider` is `pos/infra/client/mock/MockPosProvider` (owner, 2026-11-21).

- **Interface:** `pos/infra/client/PosProvider`, read methods only (rule 1), one per endpoint above:
  `merchant`, `bills` (`from`, `till`, `modifiedSince`, `limit`, `lastBillId`, `includeDeleted`;
  opaque `nextPage` cursor, which the mock keys on (sort key, bill ID) so changes between pages
  shift nothing), `billDetail`, `deskView`, `menu`. There is no staff endpoint: staff exist only
  as `createdBy` / `paidBy` on bills. Provider types (`PosModels.kt`) carry only the verified fields
  above, money as decimal strings; `pos/domain/PosMoney` turns them into minor units (fails on
  sub-minor values, never rounds).
- **Gating:** `onemind.pos.provider` (`POS_PROVIDER`), empty by default (no provider: the sync is
  skipped). The mock lives in the dev-only `mock` source set (`src/mock`), with `application-local.yml`
  setting `mock`: it is on the `bootRun` (profile `local` by default) and test classpaths, never in the
  boot jar (`verifyBootJarExcludesMock`, part of `check`). The mock bean is also `@Profile("!prod")`,
  and a `prod` start with `mock`, in any spelling, fails (`PosProviderConfig`), so demo data can never
  be shown as a real venue's figures (rule 2).
- **Data:** generated by code, deterministic per place and night (no data files in the artifact).
  One merchant `demo-merchant` with "Demo · Bar Letná", "Demo · Bistro Karlín" (active) and a
  disabled place without bills; 24 products in 5 categories, 3 sections with 12 desks and a virtual
  takeaway desk, 6 staff persons. About 26–161 paid bills per night (weekday- and place-weighted), tabs opened
  16:00–02:30 Europe/Prague and paid up to 04:55, so many cross midnight; a bill paid at 02:30 every
  night, once per offset on the autumn DST night and at 03:30 on the spring night. ~1 % refunded
  (shown, and `lastModifiedAt` moved, 20 h after payment), ~1 % whole deleted (10 min after payment,
  only with `includeDeleted`), item discounts with `discountType` `HAPPY_HOUR` (16:00–18:00 drinks),
  `EMPLOYEE`, `LOYALTY`, `MANAGER`. Nothing after the clock's now, up to 120 days back.
- **Assumptions to check against the real API** (they shape only the mock): `from` / `till` bound
  `paidAt`; `modifiedSince` is inclusive; item `price` is the unit price with VAT; `finalPrice`
  excludes tips and the payments add up to `finalPrice` + `tips`; raw values `card` / `cash` and the
  discount type names are placeholders.

## Sync strategy (updated)

Built so far (`pos/application`, task `pos-sync` every 2 minutes, `RunPosSync`): for every connection
that isn't `REVOKED`, places → venues on **every run** (one `merchant` call, cheap), then menu and desks
of each venue with an active link at most hourly (`sync_cursor` `MENU`/`DESKS`; after a failure also at
most hourly, so a bad menu isn't refetched every run).

- **Places (pending owner confirmation, orchestrator decision 2026-11-22):** a place first reported
  active becomes a venue of the connection's organization through `account/api/VenueRegistry` plus a
  `pos.venue_link`; a place first seen disabled/hidden gets no venue. A known place takes over name and
  address. A place reported `DISABLED`/`HIDDEN`, or no longer reported, **never disables the venue**:
  only its link becomes inactive (`venue_link.active`, V008), the sync skips it and the owner keeps
  reading its history. Reported active again → link active again.
- **Catalog:** rows upserted by `(venue_id, external_id)`; products and desks the POS stops reporting get
  `active = false` / `removed = true`, never deleted. A product row that can't be read exactly (e.g. a
  sub-minor price, which is never rounded) is skipped, its ID logged, and keeps what was stored; the
  rest of the menu is stored and the step counts as a success.
- **Failures:** each run writes a `sync_job` with its steps and always finishes it. A places failure
  marks the connection `ERROR` (`last_error` = exception class, never POS text); a venue failure is a
  failed step and an error on that venue's cursor only. A revoked connection is never set back to
  `ACTIVE`/`ERROR`. Nothing stops the other connections or venues.
- POS calls never run inside a transaction (tested). Without a configured provider the run does nothing
  and logs one warning.

Bills (`SyncBills`, each run, after menu and desks): the `sync_cursor` `BILLS` cursor is
`INITIAL|<next business day>|<import start>` while the initial import runs, then `SINCE|<instant>`.
The initial import fetches `onemind.pos.sync.initial-days` (default 90) business days, one `from`/`till`
window per day (the venue's zone and cut-off), oldest first; the cursor moves after each finished day,
so an interrupted import resumes at that day, and ends at `SINCE|<import start>` so nothing changed
during the import is missed. Then `modifiedSince` = cursor (inclusive), `includeDeleted=true`. For each
page, only bills whose `lastModifiedAt` differs from the stored one get a `billDetail` call; then one
transaction upserts staff (`createdBy`/`paidBy`, per connection), upserts the bills by
`(venue_id, external_id)` (unchanged ones are skipped), replaces the items, discounts and payments of
changed ones, moves the cursor and publishes `BillsSynced(venueId, businessDays)` for the days written,
only when something was written. `business_day` comes from `paidAt` (M1 seam:
`pos/domain/BillAttribution`). Desk, product and staff references resolve by POS ID; unknown ones stay
null. `raw_payload` is not written yet.

Assumptions in the bill mapping, to verify against the real API: item `price` is the unit price with
VAT and `amount` the quantity; line `total_minor` = price × amount (rounded half-up to the minor unit)
− the item's discounts; a discount's amount is `absoluteValue`, else `percentValue` % of
`originalPrice`, else 0 (the raw discount row is still stored); `finalPrice` excludes `tips`;
`payment.method_kind` is `CARD` / `CASH` when the raw method contains "card" / "cash", else `OTHER`
(raw value kept in `method_raw`).

Assumptions in the catalog mapping, to verify against the real API: product `price` is the price
with VAT in the merchant currency; `vatRate` is a percentage (`"21"`) and is stored as given;
**sub-minor menu prices** (more fraction digits than the currency) are not expected; such a product is
skipped until the owner decides how to treat them.

- **Polling only.** Per place: bills with `modifiedSince` = last seen `_lastModifiedAt`, paging with
  `nextPage` / `lastBillId`; fetch details for new/changed bills (items). Menu and desks refreshed
  hourly; places on every run (one cheap call), see above.
- Initial import: bills with `from`/`till` windows going back up to the agreed history (90 days in the
  design; the API limit is undocumented).
- Idempotent upserts keyed by `(provider, externalId)`; bills can change (refunds) after payment.
- Polling interval while the venue is open: target ~2 minutes; back off when closed. Respect rate
  limits (to be confirmed).
- After each batch publish `BillsSynced` (ADR 0013, `pos/api/events`) for reporting and stock usage.

## Questions for Storyous

1. How does a merchant assign / authorise our app (partner registration, marketplace, back-office
   switch)? Can the owner do it self-service? How do we list the merchants assigned to us?
2. Rate limits for API endpoints; maximum history for bills.
3. Is there any access to open (unpaid) bills, item-level deletions (storno), staff attendance /
   shifts, table seats?
4. Is there a replacement for DataSync webhooks?
5. Terms for a third-party analytics app (pricing, data use).
