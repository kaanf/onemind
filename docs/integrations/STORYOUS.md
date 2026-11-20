# Storyous POS integration

> **Status: unverified.** The Storyous API capabilities below are what the product *needs*, taken
> from the design. Before implementing, check the official Storyous API docs and fill in the
> "Verified" column. Do not assume an endpoint exists.

## Rules

- **Read-only.** We only call read endpoints. Never create, update or delete anything in Storyous.
  Enforce this in code: the Storyous client exposes only `get*`/`list*` methods.
- Credentials (tokens, client secret) are stored encrypted, server-side only. The mobile app never
  sees Storyous credentials.
- All Storyous access goes through one backend module behind a `PosProvider` interface, so other POS
  systems can be added later.

## Connection flow (A3 → A4)

1. App calls backend `POST /pos/storyous/connect` → gets an authorisation URL.
2. App opens it in the system browser / auth session. The **owner** signs in on Storyous and
   approves access.
3. Storyous redirects to the backend callback (or the app deep link, then the app forwards the code
   to the backend). **(?)** Depends on what Storyous supports (OAuth 2 vs API keys issued in the
   Storyous back office).
4. Backend stores the credentials, creates a `PosConnection(ACTIVE)` and starts an `INITIAL`
   `SyncJob`.
5. *"I'm not the owner, invite them"*: sends an invite to the owner's email to complete the
   connection.

## Data we need

| Data                                   | Used for                                          | Verified |
|----------------------------------------|---------------------------------------------------|----------|
| Places / venues (name, address)        | A4, A5, venue switcher                            | ☐ |
| Menu: products, categories, prices, VAT| Products by hour, recipes, margins                | ☐ |
| Happy Hour / discount rules            | Discounts & losses, Happy Hour card               | ☐ |
| Tables (name, seats, zone/area, layout)| Tables, occupancy                                 | ☐ |
| Staff (name, role)                     | Staff, waiter detail                              | ☐ |
| Staff sign-ins (clock in/out)          | Shifts, hours                                     | ☐ |
| Bills with items, order time, table, waiter, guest count | Everything in Sales/Today/Tables/Staff | ☐ |
| Open bills (live)                      | Open tabs, occupancy now, seated                  | ☐ |
| Payments (method, amount, tip)         | Card/cash split, tips                             | ☐ |
| Deleted items (storno) with who/when   | Possible loss, waiter detail                      | ☐ |
| Refunds, bills closed unpaid           | Possible loss                                     | ☐ |
| Webhooks / change feed                 | Near-real-time updates                            | ☐ |

## Sync strategy (proposed)

- **Initial sync** (A4), in steps that the app shows with progress:
  1. Venues → 2. Menu and categories → 3. Tables and staff → 4. Sales history (last **90 days**,
  paged, with %) → 5. Happy Hour and discounts.
  The app is usable after steps 1–3; history continues in the background.
- **Incremental sync**: poll for changed/new bills every *N* minutes while the venue is open
  (target: "synced 2 min ago"), or use webhooks if available. Back off when closed.
- **Live data** (open tabs, occupancy, on shift): short polling interval or on-demand refresh when
  the Today screen is open. **(?)** Rate limits.
- Idempotent upserts keyed by `(provider, externalId)`. Bills can change until closed; re-fetch
  open bills each cycle.
- After each sync batch: recompute affected aggregates and theoretical stock usage, then emit
  events (for briefing/suggestion refresh).
- Store raw payloads (short retention) for debugging and re-processing.

## Failure handling

- Token revoked / expired → `PosConnection.status = ERROR`, show a reconnect banner in the app.
- Partial sync failure → mark the step `FAILED`, retry with backoff, keep already-imported data.
- Never block the app on sync. Show "last synced" time.

## Open items

- Auth mechanism, rate limits, webhook support, data retention limits for history.
- Whether guest count, table layout and staff sign-ins are actually available.
- Multi-place accounts: one connection with several places (design assumes yes: *We found 2
  venues on your Storyous account*).
