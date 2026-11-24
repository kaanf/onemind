# 0006 · Read-only offline cache, offline stock counting

- **Status:** Accepted (local storage amended by [ADR 0011](0011_MOBILE_ARCHITECTURE.md); §1 loading amended 2026-11-24)
- **Date:** 2026-11-20
- **Deciders:** Kaan

## Context

Owners open the app anywhere, sometimes with a bad connection, and should still see the last known
figures. Stock counts (S3) happen in back storage and cellars, where there is often no signal. A
count takes several minutes, and losing it would be painful. Other write actions (approving an
invoice, sending an order, chatting) need the server anyway.

## Decision

### 1. Read-only cache for the whole app

- The last successful response of each screen is stored locally, keyed by venue + endpoint +
  parameters.
- When a screen opens (or its venue scope or period changes), it shows its loaded layout with
  skeletons in the value slots and asks the server. Cached data is shown only when that request fails
  (*"Last updated 21:40 · offline"* after a network failure), or when it has not answered within
  **5 seconds** (`CachedFallbackDelay`, *"Last updated 21:40"*); the request keeps going and its
  answer replaces the cached data. Without cached data the skeletons stay until the answer or the error.
  *Amended 2026-11-24 (owner):* this replaces "shows cached data at once, then refreshes", because old
  figures jumping to new ones on every open read as wrong numbers.
- Background polls and pull-to-refresh keep the figures on screen while they run (no skeletons), and so
  does coming back to a screen (tab switch, lock): the last figures of the same scope and period stay
  and refresh silently. Skeletons only on a first load and on a scope or period change.
- Cached data is never edited locally. The server is always the truth.
- The cache is cleared on logout and when switching accounts.

### 2. Offline stock counting (the only offline write flow)

- Starting a count downloads the count sheet: items, locations and **expected** quantities.
- Each counted line is saved **locally first**, then put in an **outbox** queue.
- The outbox sends lines when online, in order, with retries. Each request carries an
  `Idempotency-Key` (client-generated UUID), so a retry never applies twice.
- Line conflicts (two devices counting the same item): **last write wins** per
  `(countId, stockItemId)`, using the client's counted-at time. The server keeps every submission
  for audit.
- **Completing** a count can be queued offline too. The server recomputes variances against the
  stock movements at the moment it processes the completion, not at the moment the count started.
- The UI shows the sync state: *"3 lines waiting to sync"*.
- Other flows (pull list confirmation, corrections) may adopt the same outbox later, but only by a
  new decision.

### Local storage

- **DataStore** for the read cache (last response per screen key) and settings, as in crew-kmp.
- A database only when offline stock counting is built: **Room** (KMP), via crew-kmp's Room
  convention plugin, for count sheets and the outbox.

## Alternatives considered

- **Online only:** simplest, but the cellar count use case would fail.
- **Full offline sync for all data:** large effort (conflict resolution everywhere) for little value,
  because the POS data changes on the server anyway.

## Consequences

- The backend must support `Idempotency-Key` on count endpoints and accept late, out-of-order
  count lines.
- The app needs a small sync engine (outbox + retry + status) and a local database once offline counting is built.
- Every screen must handle "showing cached data" as a state, alongside loading, content, empty and
  error.
