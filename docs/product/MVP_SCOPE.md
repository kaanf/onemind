# First release (MVP) scope

What the first release contains. Anything not listed here is later, even if it is designed.
Agents: don't build out-of-scope features unless asked.

## Users

- **Owner only.** No `STAFF` accounts in the first release. Keep `AppRole` in the model so staff
  can be added later, but don't build staff invitation, staff permissions or approval flows.

## AI in the first release

- **The only AI feature is purchase receipt extraction** (S6): from a receipt/invoice photo or PDF,
  extract the meaningful product lines and show them for the owner to check, correct and add to
  stock.
- **No assistant**: no chat, no briefing, no LLM-written suggestions, no knowledge documents
  (05, 05a are later). Hide the Assistant tab; the tab bar has 4 tabs (Today, Sales, Staff, Stock).
- Hint/insight cards on dashboard screens (*Best corner is the window*, *Thursdays after 21:00
  grew 18%*, *above usual* deleted items, *9 deleted items came from one waiter*) are produced by
  **rule-based detectors with fixed text templates**, not an LLM. If a detector is not ready, the
  card is hidden.

## In scope

| Area | Screens | Notes |
|------|---------|-------|
| Onboarding | A1–A5 | Apple, Google, email code; connect Storyous; first sync; pick venues |
| Dashboard | 01, 01a, 02, 02a, 02b, 02c, 03, 03a | Read-only POS analytics |
| Purchase receipts | S6 | Photo / PDF / ISDOC → lines → match or create item → add to stock. See `docs/domain/STOCK.md#purchase-receipts-how-stock-comes-in-s6` |
| Recipes | S7 (recipe part) | Needed so sales reduce stock. Excel import via a column-mapping screen (no AI) + manual edit |
| Locations and transfers | S4, S5 | User-defined inventories (*Bar*, *Back storage*, *Home*…), stock per location, transfers between them, par levels and pull list. Create/edit form needs design |
| Counts | S3 | On demand, whenever the owner wants: full, per location, per area or selected items. The first count is the opening stock. See `docs/domain/STOCK.md#counts-s3` |

## Out of scope (later)

- Staff accounts, staff → owner approval.
- Assistant (05, 05a): chat, briefing, AI suggestions, knowledge documents and their processing.
- Order plan (04, S1); needs a few weeks of reliable stock data first.
- Idle stock (S2).
- Corrections logging (S7 lower part).
- Sending orders to suppliers automatically.

## Stock tab in the first release

The design's Stock tab opens on *Order plan | Idle stock | Count*, two of which are out of scope.
In the first release the Stock tab opens on **locations and stock value (S4)**; adding a receipt
(S6), starting a count (S3), transfers (S5) and recipes (S7) are reached from there.

**Design to be added** (owner will add to the canvas): Stock tab landing for the first release,
location create/edit form.

## Adjustments from the Storyous API check (2026-11-20)

The public Storyous API only returns **paid** bills and has no staff attendance, item-level voids
or table seats (`docs/integrations/STORYOUS.md`). In the first release:

- **No shifts or hours.** Staff screens show sales, tips, guests and bills per person (from bill
  `createdBy` / `paidBy`). Shifts come later through a staff clock-in on their own phones.
- **No live occupancy, open tabs or seated guests.** Today drops those cards; Tables shows only
  *This week* (no *Now* tab). No manual table status entry (it would mean managing two systems).
- **Deleted items** = whole deleted bills only (`includeDeleted`); item-level voids are not
  available.
- **Products by hour** use the bill's session start (`sessionCreated`); there is no per-item order
  time.
- Values derived indirectly (e.g. estimated table capacity from the highest guest count seen) must
  be labelled as estimates and defined in `docs/domain/METRICS.md`.
- These may change if a Storyous partnership gives access to more data
  (see `docs/product/STORYOUS_PARTNERSHIP.md`).

