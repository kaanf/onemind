# Metrics

Exact definitions of every number shown in the app. **Backend computes, mobile displays.** The
mobile app must not re-derive these from raw data. If a definition changes, change it here first.

Items marked **(?)** need confirmation; see `../OPEN_QUESTIONS.md`.

## Business day

- A venue's business day starts at its `businessDayStart` cut-off (default **05:00** local time)
  and ends just before the next one. A bill belongs to the business day in which it was
  **closed** (paid); open tabs belong to the current business day. **(?)**
- All times are in the venue's time zone (`Europe/Prague`). Store timestamps in UTC; convert at
  aggregation time. Handle DST days (23/25 h).
- "Today" = current business day. "Last Friday" = the business day exactly 7 days earlier.
- Implemented once, in `backend/…/common/time/BusinessDayCalendar` (cut-off = the venue's
  `business_day_start` setting, R28); reading the system clock anywhere else fails the build (`architecture/ProductRules`).
- Rollups store the business date (and local hour) computed by `BusinessDayCalendar` in Kotlin as
  columns. SQL date functions (`date_trunc`, `AT TIME ZONE`, `::date`) never decide a business day or
  an hour bucket.

## Periods and comparisons

| Period | Range                                   | Compared with                                  | Copy example                          |
|--------|-----------------------------------------|------------------------------------------------|---------------------------------------|
| Hour   | current clock hour                      | same hour, same weekday last week              | *+9.2% vs last Fri, same hour*        |
| Day    | current business day so far             | same weekday last week, **up to the same time** | *+12.4% vs last Friday*               |
| Week   | Mon–Sun (ISO week) containing today     | previous week, same elapsed span **(?)**       | *+8.1% vs last week*                  |
| Month  | calendar month                          | previous month, same elapsed span **(?)**      | *+3.6% vs October*                    |

`delta% = (current − previous) / previous × 100`, one decimal. Not shown when previous is 0.

## Revenue

| Metric           | Definition |
|------------------|------------|
| `paidRevenue` (*Paid so far*, *Revenue*) | Sum of payment amounts **excluding tips**, for bills paid in the period. Gross incl. VAT **(?)**. Refunds subtracted in the period of the refund. |
| `openTabsTotal`  | Sum of current totals of bills with status `OPEN`. Only for "now". |
| `openTabsTables` | Number of distinct tables with an open bill. |
| `cardRevenue`, `cashRevenue` | `paidRevenue` split by payment method. `cardPct = card / (card + cash)`, rounded to an integer; cash% = 100 − card%. Other methods: **(?)** |
| `tips`           | Sum of bill `tips` for bills paid in the period. *Tips · card 69%* = card tips share. First release: the tips total is shown, the card share is not (Storyous gives tips per bill only; no inference from payments). |
| `guests`         | Sum of `guestCount` on bills paid in the period ("since opening" for today). If the POS has no guest count for a bill, count 1 **(?)**. |
| `averageBill`    | `paidRevenue / number of paid bills`. |
| `revenuePerGuest`| `paidRevenue / guests`. |
| Hourly chart (Today) | `paidRevenue` per clock hour from the venue's opening hour. Future hours = average of the same hour over the last 4 same weekdays (shown in a lighter colour). |

## Occupancy and tables

| Metric | Definition |
|--------|------------|
| `occupancyNow` | `tables with an open bill / total active tables` (*10 / 13*). Bar counter counts as one table. |
| `seatedNow`    | Sum of `guestCount` on open bills (*41 seated*). |
| Table *Since*  | `openedAt` of the earliest open bill on that table. |
| Table *Last guest* | `closedAt` of the last paid bill on that table today. |
| `tableRevenue` (week) | `paidRevenue` of bills on that table in the period. |
| `revenuePerSeat` | `tableRevenue / seats`. Heat ramp bucket = `floor(revenuePerSeat / 650)` capped at 5. The 650 Kč step should become relative (quantiles) instead of fixed **(?)**. |
| Table *Per guest* | `tableRevenue / guests on that table`. |
| Table *Occupied %* | Share of open hours the table had an open bill. |

## Products and hours

| Metric | Definition |
|--------|------------|
| Product *sold* | Sum of `BillItem.qty` for the product in the business day, **excluding deleted items**. |
| Product *gross* | Sum of `qty × unitPrice − discount` for those items. |
| Product hourly | Bucketed by `orderedAt` hour. |
| Peak hour | Hour with the max qty (first one wins on ties). |
| Hourly patterns | For each weekday × hour: average guests over the **last 8 weeks** (bills bucketed by `openedAt` hour; guests counted in the hour the bill opened) **(?)**. |
| Peak / Quietest window | Two-hour window with max / min average guests in opening hours. |

## Discounts and losses

| Metric | Definition |
|--------|------------|
| `discounts` | Sum of discount amounts in the period, grouped by type (Happy Hour, staff, comp, loyalty). |
| `deletedItems` | Sum of amounts of items deleted after ordering. |
| `refunds` | Sum of refunded amounts. |
| `unpaidClosed` | Sum of totals of bills closed without payment. |
| `stockCorrections` | Value at **sale price** **(?)** of corrections logged in S7. |
| `possibleLoss` | `discounts + deletedItems + refunds + unpaidClosed + stockCorrections`. First release: `discounts + refunds + deletedItems` (whole deleted bills only); the screen lists the included components (`product/MVP_SCOPE.md#data-sources-in-the-first-release`). |
| Loss share | `possibleLoss / paidRevenue` (*6.7% of revenue*). Change shown in **percentage points** (*+0.8 pts*). |
| Records | Count of individual discount/deleted/refund/unpaid records. |

### Happy Hour net effect

- *Discount* = sum of Happy Hour discounts in the period.
- *Extra sales* = revenue in Happy Hour windows minus the **baseline** revenue for the same windows
  without Happy Hour. Baseline method is an open question (e.g. same hours on weeks or days without
  HH, or hours just before/after). Must be documented in the UI when shown.
- *Net* = Extra sales − Discount.

## Staff

| Metric | Definition |
|--------|------------|
| Hours | Sum of shift durations from POS sign-ins in the business day (ongoing shifts up to now). Team *HOURS* shown as `h:mm`. |
| Staff *sales* | `paidRevenue` of bills **opened** by the staff member (POS `createdBy`). |
| Staff *tips* | Tips on bills **closed** by the staff member (POS `paidBy`). |
| Staff *guests* | Guests on those bills. |
| *Bills paid*, *Open tabs* | Counts for that staff member. |
| *Deleted* | Count of deleted items by that staff member today. |
| Usual deleted | Interquartile range (P25–P75) of deleted items per shift for that person over the last 8 weeks (*"usual 2 to 3"*). Flag "above usual" when today > P75 + max(2, IQR) **(?)**. |

## Stock

Stock formulas live in [STOCK.md](STOCK.md).

## Formatting rules (shared with mobile)

- CZK: no decimals for amounts ≥ 100 Kč in summaries; recipe costs show one decimal (*40.7 Kč*).
- Thousands separator: narrow no-break space (U+202F) or no-break space; currency after the number.
- Negative: `−` (U+2212), not hyphen. Positive deltas show `+`.
- Percentages: deltas one decimal (*12.4%*), shares integer (*69%*).
- Large chart labels: `12.4k`.
