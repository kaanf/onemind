# Metrics

Exact definitions of every number shown in the app. **Backend computes, mobile displays.** The
mobile app must not re-derive these from raw data. If a definition changes, change it here first.

Items marked **(?)** need confirmation; see `../OPEN_QUESTIONS.md`.

## Business day

- A venue's business day starts at its `businessDayStart` cut-off (default **05:00** local time)
  and ends just before the next one. A bill belongs to the business day in which it was
  **closed** (paid); open tabs belong to the current business day (owner decision 2026-11-22, M1
  closed). The sync stores `pos.bill.business_day` this way through one seam,
  `pos/domain/BillAttribution`.
- All times are in the venue's time zone (`Europe/Prague`). Store timestamps in UTC; convert at
  aggregation time. Handle DST days (23/25 h).
- "Today" = current business day. "Last Friday" = the business day exactly 7 days earlier.
- Implemented once, in `backend/…/common/time/BusinessDayCalendar` (cut-off = the venue's
  `business_day_start` setting, R28); reading the system clock anywhere else fails the build (`architecture/ProductRules`).
- Rollups store the business date (and local hour) computed by `BusinessDayCalendar` in Kotlin as
  columns. SQL date functions (`date_trunc`, `AT TIME ZONE`, `::date`) never decide a business day or
  an hour bucket. Gated by `backend/…/architecture/DateFunctionRules`: migrations, SQL text in Kotlin
  and jOOQ date functions (`DSL.trunc`/`extract`/`hour`/`timezone…`, `DatePart`, casts to date/time
  `SQLDataType`s).

## Where the figures are stored

Business-day figures come from the `reporting` rollups (`architecture/DB_SCHEMA.md#reporting`), one row
per venue × business day × hour / product / staff member / desk / discount type, computed once in
`reporting/domain/RollupCalculator` from POS facts (`pos/api/PosReadModel`) and refreshed on
`BillsSynced`. Deleted bills count only as deleted (`deleted_bills`, `deleted_minor`).
`revenue_minor` is `paidRevenue` (payments without tips, so card + cash + other = revenue) and
**includes refunded bills**: readers subtract `refunded_minor`. `revenue_without_tax_minor` sums only
bills that have a net total; `bills_missing_net` counts the others, and a screen doesn't show the net
figure while it is above 0 (M2 open). A bill whose payments don't add up (tips above the payments, or a
total without any payment) is **left out** of revenue and the card/cash/other split, never clamped:
`bills_payment_mismatch` counts it, and screens show it as a data gap with the others
(`RollupReads.dataGaps`). Bills that
couldn't be read are missing from them; screens show that gap (`RollupReads.dataGaps`, rule 2).

## Periods and comparisons

| Period | Range                                   | Compared with                                  | Copy example                          |
|--------|-----------------------------------------|------------------------------------------------|---------------------------------------|
| Hour   | current clock hour                      | same hour, same weekday last week              | *+9.2% vs last Fri, same hour*        |
| Day    | current business day so far             | same weekday last week, **up to the same time** | *+12.4% vs last Friday*               |
| Week   | Mon–Sun (ISO week) containing today     | previous week, same elapsed span **(?, M3)**   | *+8.1% vs last week*                  |
| Month  | calendar month                          | previous month, same elapsed span **(?, M3)**  | *+3.6% vs October*                    |

`delta% = (current − previous) / previous × 100`, one decimal, rounded half-up. Not shown when previous
is 0 or missing.

- **Today (Day period):** "up to the same time" is the same **local clock time** on the business day 7
  days earlier (`BusinessDayCalendar.sameLocalTimeDaysEarlier`, so a DST change in between doesn't
  shift it). Hourly rollups can't cut mid-hour, so the earlier figure is computed from that day's POS
  facts with the same per-bill revenue rule as the rollups (`reporting/domain/BillRevenue`). It covers
  the same venues on both sides: only those that have that earlier day (a venue synced since would
  make up growth); the response names them, and without any there is no comparison.
- Several venues share one Today or Sales only when they share one time zone and cut-off (one chart,
  one clock; `422 BUSINESS_DAYS_DIFFER` otherwise) and one currency: a first-release limit.

**Sales (02).** Ranges are business days (`DAY` = one business day, `WEEK` = Monday to Sunday, `MONTH`
= the calendar month, `HOUR` = the current clock hour of today). *Choose dates* picks a period
containing an earlier day (never after today); arbitrary from/to ranges come later.

**Same elapsed span (M3 default, (?)).** A period that contains today is compared with the same part of
the previous period: as many whole business days from its start, then the next day **up to the same
local time** as now (Day and Hour are the one-day case). A finished period is compared with the whole
previous period.

- Week, Wednesday 18 Nov at 21:40: Mon 16 – Wed 18 so far vs Mon 9 – Tue 10 whole + Wed 11 up to 21:40.
- Month cap: on day N of a month, the previous month's days 1 to N−1 whole + day N up to the same time;
  when the previous month has no day N, the **whole** previous month (on 29, 30 or 31 March of a
  non-leap year: all of February).
- DST: the cut is the same local clock time, not the same elapsed hours. On Sunday 29 March 2026 (the
  23-hour spring day in Prague) at 21:40, last Sunday is cut at 21:40 local time.
- Hour: the current clock hour so far vs the same hour of the same weekday last week up to the same
  minute (*21:00 to 21:40* vs *21:00 to 21:40*).
- Whole days come from the hourly rollups, the cut day from that day's POS facts (as on Today).
- **"Same local time" = the venue's last successful bills sync (`dataAsOf`), not the request time**, on
  Today and Sales alike: between syncs the current figures stop at the last sync, so the earlier side is
  cut at the same point (else the delta would lean downward until the next sync).
- `averageBill` and the payment split stay hidden (`null`) until the rebuild job has moved **every day
  of the period** to the current rollup version (`RollupVersion.CURRENT`; V013 added their columns).
- **Same venues:** the venues whose data (first rollup day) starts on or before the compared span's
  first day; a closed day inside the span is a real 0 (Today asks for the compared day itself). None:
  no comparison.

## Revenue

| Metric           | Definition |
|------------------|------------|
| `paidRevenue` (*Paid so far*, *Revenue*) | Sum of payment amounts **excluding tips**, for bills paid in the period. Gross incl. VAT **(?)**. Refunds subtracted in the bill's own paid business day, since Storyous has no refund time (owner, 2026-11-22; M13 closed, R29). |
| `openTabsTotal`  | Sum of current totals of bills with status `OPEN`. Only for "now". |
| `openTabsTables` | Number of distinct tables with an open bill. |
| `cardRevenue`, `cashRevenue` | `paidRevenue` split by payment method (refunded bills taken off by their own split, so card + cash + other = `paidRevenue`). `cardPct = card / (card + cash)`, rounded half-up to an integer; cash% = 100 − card%. Other methods (vouchers, meal cards) are sent as an amount and left out of `cardPct` **(?, M4)**. Payments include tips, so a bill's tips are taken off its card payments first, then cash, then other: an approximation for bills paid partly by card and partly in cash, since Storyous gives tips per bill only (owner, 2026-11-22; M14 closed, R30). |
| `tips`           | Sum of bill `tips` for bills paid in the period. *Tips · card 69%* = card tips share. First release: the tips total is shown, the card share is not (Storyous gives tips per bill only; no inference from payments). |
| `guests`         | Sum of `guestCount` on bills paid in the period ("since opening" for today). If the POS has no guest count for a bill, count 1 **(?, M5)**. |
| `averageBill`    | `paidRevenue / number of paid bills`, half-up: bills that count in `paidRevenue` (not deleted, not refunded, payments add up; `hourly_sales.paid_bills`). Not shown while a day of the period is still stored by an older rollup calculation (rule 2). |
| `revenuePerGuest`| `paidRevenue / guests`. |
| Hourly chart (Today) | `paidRevenue` per local clock hour over the hours that have sales at the venue(s) (owner decision 2026-11-22, M12 closed): every hour with paid revenue today or on any of the last 4 same weekdays, contiguous from the first to the last in business-day order (the day's own local hours: the hour skipped on the spring DST day has no bar), plus the current hour. Past hours = paid in that hour; current hour = paid so far; future hours = the average of that hour over those of the last 4 same weekdays that have data (an hour without sales on such a day counts 0), rounded half-up; no forecast without any earlier same weekday. Several venues add up hour by hour (forecast = sum of each venue's average). |

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
| Product *sold* | Sum of `BillItem.qty` for the product in the business day, **excluding deleted items** (first release: lines of whole deleted bills). Per venue: POS product ids are per venue, products are never merged across venues by name. |
| Product *gross* | Sum of `qty × unitPrice − discount` for those items. The list is sorted by gross, largest first, then by name, then by product id (stable output). |
| Product hourly | Bucketed by the hour the bill was opened (session start, else paid time): Storyous has no per-item order time (`product/MVP_SCOPE.md`). |
| Peak hour | Hour with the max qty (on ties the first in business-day order: 23:00 before 01:00). |
| Products axis (02a) | The day's local hours with product sales, in business-day order (the data-derived axis of the Today chart, M12); none on a day without sales (a real empty day, not "no data"). |
| Hourly patterns | For each weekday × hour: average guests over the **last 8 weeks** (bills bucketed by `openedAt` hour; guests counted in the hour the bill opened) **(?)**. |
| Peak / Quietest window | Two-hour window with max / min average guests in opening hours. |

## Discounts and losses

| Metric | Definition |
|--------|------------|
| `discounts` | Sum of discount amounts in the period: the POS **bill-level** `discount` of bills that aren't deleted (also on Today). By type (02c): item discounts by POS `discountType` (`UNSPECIFIED` = an item discount without a type), plus one `UNTYPED` row (*Other discounts*, no record count) = bill-level − typed, per venue and business day, when the bill level is higher: bill-wide discounts and the items of bills stored without lines. So the list adds up to `discounts`. A venue day whose typed discounts are higher shows its typed ones only, is logged (WARN, venue and day) and counted in `dataGaps.discountMismatchDayCount`; never clamped. |
| `deletedItems` | Sum of amounts of items deleted after ordering. |
| `refunds` | Sum of refunded amounts. |
| `unpaidClosed` | Sum of totals of bills closed without payment. |
| `stockCorrections` | Value at **sale price** **(?)** of corrections logged in S7. |
| `possibleLoss` | `discounts + deletedItems + refunds + unpaidClosed + stockCorrections`. First release: `discounts + refunds + deletedItems` (whole deleted bills only); the screen lists the included components (`product/MVP_SCOPE.md#data-sources-in-the-first-release`). |
| Loss share | `possibleLoss / paidRevenue` (*6.7% of revenue*). Change shown in **percentage points** (*+0.8 pts*). |
| Records | Count of individual discount/deleted/refund/unpaid records. First release: item discount records + refunded bills + whole deleted bills. |

Discounts & losses (02c) uses the Sales periods (`DAY`, `WEEK`, `MONTH`; no `HOUR`) and comparison rules
(same elapsed span, same venues, cut at the last sync); the loss share's change is in percentage points,
one decimal half-up, from the exact shares. Version-dependent figures (record counts, `averageBill`, the
payment split) are hidden while **any** day of the period is stored by an older rollup calculation than
`RollupVersion.CURRENT` (rule 2).

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
