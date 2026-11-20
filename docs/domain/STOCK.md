# Stock and purchasing

How the Stock tab works (screens 04, S1–S7). Storyous gives us **sales**. Everything about stock
(items, locations, recipes, counts, deliveries) is **our own data**, built from user input,
uploaded documents and invoices.

## Core idea

```
on hand (now) = last count
              + deliveries (approved invoices)
              − theoretical usage (POS sales × recipes)
              ± transfers (per location; venue total unchanged)
              − corrections (spilled, remade, comp, welcome drink)
```

Everything is a `StockMovement` (append-only). Counts reset the baseline with a
`COUNT_ADJUSTMENT` movement equal to the variance. Nothing is ever deleted.

## Theoretical usage

- For each sold `BillItem` (not deleted) whose product has a `Recipe`, create usage =
  `ingredient.qty × billItem.qty` per ingredient, at the venue's **sales location** (see Locations).
  Per-product or per-POS-area sales locations are a later option.
- Products without a recipe but mapped 1:1 to a stock item (a bottled beer) use 1 unit per sale.
- Products with no recipe and no mapping produce no usage and are listed as "missing recipe".
- Usage is computed incrementally after each sync, idempotent per bill item.
- Example (S7): *46 Negroni sold → 1.38 L gin, 1.38 L Campari, 1.38 L vermouth taken from Main bar*.

## Recipes and cost

- Source: manual entry or import from a spreadsheet (*Cocktail costs.xlsx*). Import is
  assistant-assisted: parse rows, propose recipes, user confirms. **(?)**
- `costPerServing = Σ ingredient.qty × stockItem.unitCost / unitSize`.
- `margin = (price − costPerServing) / price`. VAT handling: price net of VAT **(?)**.
- When an approved invoice changes a stock item's unit cost, recompute affected recipes and raise an
  alert: *"Gin went up 8% on the 17 Nov invoice. Each Negroni now costs 1.3 Kč more."*
- Unit cost policy: last purchase price **(?)** (alternative: weighted average).

## Corrections (S7)

- User logs *n* servings of a product that were not sold, with reason `SPILLED | REMADE | COMP |
  WELCOME`.
- Creates usage movements per ingredient (type `CORRECTION`), attributed to the user.
- Appears in *Discounts & losses* under its own line.

## Locations, par levels, transfers (S4, S5)

- **Locations are user-defined inventories.** The owner creates, renames and archives them freely,
  with any name: *Bar*, *Back storage*, *Van*, even *Home* (stock the owner keeps at home).
  A new venue starts with one location (named after the venue) so everything works before the
  owner creates more.
- Each item's quantity is tracked **per location**; the venue total is the sum.
- Exactly one location is the **receiving** default (where a receipt goes unless changed on S6,
  *Into Back storage · change*) and exactly one is the **sales** location (where recipe usage from
  POS sales is deducted). They can be the same location.
- Archiving a location with stock is blocked until its stock is transferred out or counted to zero.
- Creating/editing locations is **not designed** (S4 only lists them): needs a simple form.
- `ParLevel` per location × item. *Below par* = on hand at location < par.
- **Transfer before ordering**: if a location is below par and another location has stock, suggest
  a transfer (pull list), not a purchase. Order only when the **venue total** is below the reorder
  point.
- Pull list: generated daily "before opening" from below-par items. Each line: item, reason
  (*Shelf 1.25 · par 3*), qty = par − on hand, rounded up to sensible units. Confirming creates
  `TRANSFER_OUT` + `TRANSFER_IN` for checked lines; unchecked lines roll over to tomorrow.

## Counts (S3)

Counts are **on demand**: the owner starts one whenever they want. There is no fixed weekly
schedule (the design's *Sunday count* is only example copy).

- Scope chosen when starting: **everything**, **one location**, **one area/category**
  (*Spirits shelf*) or **selected items**. A partial count only resets the items it covers.
- The **opening count** is simply the first count of a venue: it sets the starting quantity of
  every item, so on hand doesn't start at zero and go negative with the first sales.
- Single-item quick correction (*"I have X now"*) is a one-item count with the same movement type.
- Every item shows **days since last counted**; the order plan shows it for its lines too, so the
  owner can see how fresh the numbers are. Suggesting which items to count (oldest count, biggest
  usage, biggest past variance) is a later improvement.
- For each item show `expected` (formula above, at that location).
- Input: whole units + fraction (`0, ¼, ½, ¾`) for bottles.
- `variance = counted − expected`. Highlight when `|variance| ≥ 0.5` unit **(?)** (per-item
  thresholds later).
- Finishing a count writes `COUNT_ADJUSTMENT` movements and becomes the new baseline.
- Progress and estimated time left (*12 of 38 · about 6 minutes left*).

## Order plan (04, S1)

For each supplier and its next delivery window:

1. `window` = from next delivery to the delivery after it (*Tue 10 → Tue 17 Nov*).
2. `forecast` = expected usage in the window: per weekday, average theoretical usage over recent
   weeks (default 4–8) with trend adjustment (*Spritz up 34%*). **(?)** method.
3. `safetyBuffer` = covers demand variability (*Saturdays vary by about 20%*), e.g.
   `z × σ(daily usage) × √days` or a percentage of the forecast. **(?)**
4. `onHandAtDelivery` = on hand now − forecast usage until delivery (never below 0).
5. `need = forecast + safetyBuffer − onHandAtDelivery`.
6. `qty = roundUpToCase(need)` (*rounded to a case*), 0 if need ≤ 0.
7. `atRisk` = item runs out **before** the delivery arrives (*Runs out Saturday, before this
   delivery*). Show lost sales estimate (*About 22 Spritz sales (3 500 Kč) at risk*) and a top-up
   hint (*2-bottle top-up from cash and carry*).
8. Items in *Don't buy this week*: enough stock for the window (*covers about 5 weeks*) or idle /
   excluded from ordering.

Header: order deadline (*ORDER BY MON 10:00 · DELIVERY TUE*), total cost, item and supplier count,
coverage date, number at risk.

The user can change quantities (stepper with note: one full case / below forecast / ties up cash)
and **Review and send to suppliers**. Sending channel (email, PDF, supplier portal) is an open
question. The plan never orders automatically.

Every number in the breakdown must be reproducible from stored data (S1 *How we got 6*). Store the
breakdown with the plan line.

## Idle stock (S2)

- Idle = no usage, or very slow usage (below a threshold, **(?)**), for **6+ weeks**.
- Value = on hand × unit cost (*cash tied up*).
- Suggested actions (one per item, chosen by rules or the assistant):
  - `MENU_IDEA` *Feature in a Happy Hour drink* → creates an assistant note.
  - `STOP_REORDER` → sets `excludedFromOrdering`.
  - `RETURN_REQUEST` *Ask supplier about a return* → drafts a message.
  - `BUNDLE_IDEA` *Bundle with a burger* → saves an idea.
- Idle items are excluded from the order plan until they start selling again.

## Purchase receipts: how stock comes in (S6)

In the first release this is **the** way stock enters the system: the owner enters the receipt
or invoice of what was bought, and its lines are added to stock. Sources:

- **Cash & carry receipts** (Makro and similar): small thermal paper, short abbreviated line names.
- **Supplier invoices**: A4 paper, PDF by email, sometimes **ISDOC** (Czech e-invoice XML).

Receipts also **build the stock catalog**: the first time a line can't be matched, the owner
creates a new `StockItem` from it (name, unit, unit size, case size), with the line's price as the
first unit cost.

Flow:

1. The owner photographs the receipt/invoice, or uploads a PDF / ISDOC file. Lines can also be
   added or corrected by hand.
2. Extraction, cheapest path first:
   - ISDOC / embedded XML → parsed directly (no AI). *(Verify ISDOC availability in practice.)*
   - Photo or image PDF → vision model (Claude Haiku 5.5 by default, Sonnet 5.5 if quality is
     poor) with structured JSON output: supplier, document number, date, lines (raw text, qty,
     unit, unit price, line total), document total. See `docs/ai/ASSISTANT.md`.
3. Matching per line, in order: remembered `SupplierItemAlias` → suggested match (fuzzy / LLM) →
   unmatched (*New name from this supplier*, needs **Match** or **Create new item**).
4. Unit conversion (*1 crate = 24 bottles*, `6KS` = 6 pieces) stored on the alias.
5. Price check against the last delivery (*Price up 8% since last delivery*).
6. Totals check: sum of lines must equal the invoice total (*Total, matches invoice*).
7. Target location defaults to the receiving location (*Into Back storage · change*).
8. Status `NEEDS_MATCH` until every line is matched; then `READY` (*Approve and add 9 lines to
   stock*). Approval writes `DELIVERY` movements and updates unit costs. In the first release only
   the owner uses the app, so the owner enters and approves; the scanned-by-staff → owner-approves
   flow from the design comes with staff accounts.
9. Manual matches are saved as aliases for that supplier (*Saved for this supplier*).
