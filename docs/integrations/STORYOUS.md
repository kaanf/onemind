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
  `nextPage` cursor), `billDetail`, `deskView`, `menu`. There is no staff endpoint: staff exist only
  as `createdBy` / `paidBy` on bills. Provider types (`PosModels.kt`) carry only the verified fields
  above, money as decimal strings; `pos/domain/PosMoney` turns them into minor units (fails on
  sub-minor values, never rounds).
- **Gating:** `onemind.pos.provider` (`POS_PROVIDER`), `mock` by default. The mock bean is
  `@Profile("!prod")`, and a `prod` start with `mock` fails (`PosProviderConfig`), so demo data can
  never be shown as a real venue's figures (rule 2). Prod sets the provider once the real client exists.
- **Data:** generated by code, deterministic per place and night (no data files in the artifact).
  One merchant `demo-merchant` with "Demo · Bar Letná", "Demo · Bistro Karlín" (active) and a
  disabled place without bills; 24 products in 5 categories, 3 sections with 12 desks and a virtual
  takeaway desk, 6 staff persons. 40–140 paid bills per night (weekday-weighted), tabs opened
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

- **Polling only.** Per place: bills with `modifiedSince` = last seen `_lastModifiedAt`, paging with
  `nextPage` / `lastBillId`; fetch details for new/changed bills (items). Menu, desks and places
  refreshed less often (e.g. hourly / daily).
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
