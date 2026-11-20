# Storyous POS integration

Verified against the public docs at https://docs.api.storyous.com/ on 2026-11-20 (read from the
docs' OpenAPI pages). Anything marked **undocumented** must be confirmed with Storyous.

## Rules

- **Read-only.** We only call read endpoints, never create/update/delete anything in Storyous
  (the API does offer write endpoints, e.g. stock ups; we don't use them). The Storyous client
  exposes only read methods; a test fails if a non-GET call is added.
- Credentials (client ID/secret, tokens) are stored encrypted, server-side only.
- All Storyous access goes through one backend module behind a `PosProvider` interface.

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

## Sync strategy (updated)

- **Polling only.** Per place: bills with `modifiedSince` = last seen `_lastModifiedAt`, paging with
  `nextPage` / `lastBillId`; fetch details for new/changed bills (items). Menu, desks and places
  refreshed less often (e.g. hourly / daily).
- Initial import: bills with `from`/`till` windows going back up to the agreed history (90 days in the
  design; the API limit is undocumented).
- Idempotent upserts keyed by `(provider, externalId)`; bills can change (refunds) after payment.
- Polling interval while the venue is open: target ~2 minutes; back off when closed. Respect rate
  limits (to be confirmed).
- After each batch publish `BillsSynced` (ADR 0009) for reporting and stock usage.

## Questions for Storyous

1. How does a merchant assign / authorise our app (partner registration, marketplace, back-office
   switch)? Can the owner do it self-service? How do we list the merchants assigned to us?
2. Rate limits for API endpoints; maximum history for bills.
3. Is there any access to open (unpaid) bills, item-level deletions (storno), staff attendance /
   shifts, table seats?
4. Is there a replacement for DataSync webhooks?
5. Terms for a third-party analytics app (pricing, data use).
