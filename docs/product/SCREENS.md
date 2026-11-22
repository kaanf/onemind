# Screens

Screen-by-screen specification, taken from the **Cross-platform** page of the design canvas.
Screen IDs match the artboard titles. Design file names are given so you can open the source
(`project/<File>.dc.html` in the canvas).

Example values below come from the design mock data (a Prague bar called *Main bar*). They show
format and meaning only. Never hard-code them.

Conventions used in all screens:

- Frame 390 × 844 (phone). Bottom tab bar on top-level screens, back arrow on detail screens.
- Top-level tabs: **Today · Sales · Staff · Stock · Assistant**.
- Money in CZK, formatted `48 320 Kč`. Negative amounts use a real minus `−`.
- Every figure maps to a definition in [`../domain/METRICS.md`](../domain/METRICS.md).

---

## Onboarding (A1–A5)

Shown once, on first launch. See [NAVIGATION.md](NAVIGATION.md) for the flow.

### A1 · Sign in — `AuthWelcome`

- Brand: **OneMind** (the design still shows the working title *Pour*). Headline *"Your bar, hour by hour."* Subline: *"Sales, staff, tables and losses
  from your POS, with suggestions on what to do next."*
- Buttons: **Continue with Apple**, **Continue with Google**.
- Divider *"or with email"*, field **Work email** (placeholder `owner@yourbar.cz`), button
  **Send me a code**.
- Footer: *"No password. By continuing you agree to the Terms and Privacy Policy."*
- Behaviour: passwordless. Email path goes to A2.
- **Built (phase 3):** email path only. *Continue with Apple / Google* and the *or with email*
  divider are hidden until the native provider flows land (roadmap: "Mobile: Sign in with Apple /
  Google (A1)"). *Send me a code* is enabled for a plausible address (one `@`, a dot in the domain);
  the server validates. Terms and Privacy Policy are plain text until their pages exist (phase 7).
  If a code was sent less than 30 s ago (`429 EMAIL_CODE_RESEND_TOO_SOON`), A1 goes on to A2 with the
  server's `retryAfterSeconds` as the countdown.

### A2 · Email code — `AuthCode`

- *"Check your email. We sent a 6-digit code to {email}. It expires in 10 minutes."*
- 6 single-digit inputs (auto-advance, paste whole code).
- *"Didn't get it? Resend in 0:24"*: resend is disabled until the countdown ends.
- **Verify** (primary), **Use a different email** (back to A1).
- After verify: new account → A3; existing account with connected POS → Today.
- **Built (phase 3):** the resend countdown starts at 30 s (ADR 0004) or the server's
  `retryAfterSeconds`. Errors under the code: `EMAIL_CODE_INVALID` "That code isn't right. Attempts
  left: n." (only this clears the boxes), `EMAIL_CODE_EXPIRED` and `EMAIL_CODE_ATTEMPTS_EXCEEDED` ask
  for a new code; other errors (offline, timeout) keep the code for a retry and use the shared
  messages or the server's `detail`. While a code is being checked, back, *Use a different email*,
  Resend and the code boxes are disabled, so a code the server accepted can't be lost before the
  session is stored. Back and *Use a different email* always return to A1, never past it. Until A3–A5 exist (phase 4
  interim onboarding), **every** successful verify goes to Today: the app gate switches to the tab
  shell when the session is stored. A refresh that fails with `401` (or a sign-out) returns to a fresh A1.

### App update required (426) — `UpdateScreen`

Not designed (P7). Shown over everything, sign-in included, when any call answers
`426 APP_UPGRADE_REQUIRED`: title *Update OneMind*, body with `params.minimumVersion` when sent,
**Check again** (hides it; the next call asks the server again). No store button until the App Store
and Play listings exist (phase 7).

### Interim onboarding (dev/demo builds, until P15)

Until the Storyous connection exists, a new account goes from A2 straight to a variant of A5,
**Pick demo venues**: the demo POS places (`GET /onboarding/demo-places`), every card with a visible
**Demo data** badge (product rule 2). Picking creates the account's organization ("Demo organization"),
its venues and the followed venues (`POST /onboarding/demo`) and lands on **Today**, which shows empty
states until the first sync (about two minutes). A3 and A4 are skipped (no sync-status endpoint yet).
In production the endpoints answer `404 FEATURE_UNAVAILABLE`, so this flow never appears there.

### A3 · Connect Storyous — `AuthConnect` (Step 1 of 3)

- *"Connect your Storyous account. Sign in as the venue owner. You will approve access on Storyous
  and come straight back here."*
- "What we read" list: *Sales, bills, payments and tips* · *Menu, categories and Happy Hour rules* ·
  *Tables, staff sign-ins and deleted items*.
- Trust note: *"Read only. We never change anything in Storyous."*
- **Continue to Storyous** → external authorisation, returns to the app (deep link) → A4.
- **I'm not the owner, invite them** → invite-the-owner flow (not designed).

### A4 · First sync — `AuthSync` (Step 2 of 3)

- *"Storyous connected. Importing your data. This takes a minute or two. You can start using the
  app before history finishes."*
- Progress list, each row has a state `done | running (with %) | waiting`:
  1. Venues: *2 found*
  2. Menu and categories: *142 items*
  3. Tables and staff: *13 tables · 6 staff*
  4. Sales history: *last 90 days · 64%* (progress bar)
  5. Happy Hour and discounts: *waiting*
- **Continue** is available before history is done. Footer: *"History keeps loading in the
  background."*
- Needs a sync-status endpoint the app can poll or subscribe to.

### A5 · Pick venues — `AuthVenues` (Step 3 of 3)

- *"Which venues do you want to follow? We found 2 venues on your Storyous account. You can change
  this anytime."*
- Checkbox cards: name + meta (*Vinohrady · 13 tables · 6 staff*).
- When more than one is selected: info *"All venues view is on. Today and Sales can show both venues
  together, or one at a time from the switcher."*
- CTA text depends on selection: 0 → *"Pick at least one venue"* (disabled), 1 → *"Open Today"*,
  n → *"Open Today for n venues"*.
- **Skipped** if the account has only one venue.

---

## 01 · Today — `AltToday`

Live overview of the current business day for the selected venue (or all venues).

- Header: venue switcher button (*Main bar ▾*), account avatar (initials, e.g. *KF*), date and time
  (*Friday, 6 November · 21:40*), title **Today**.
- **Revenue card**
  - *PAID SO FAR* `48 320 Kč`, delta *+12.4% vs last Friday*.
  - *Open tabs · 10 tables* `9 860 Kč`.
  - *Tips · card 69%* `3 940 Kč`.
  - Hourly bar chart (11 bars, 16:00 → 02:00): past hours grey, current hour accent, future hours
    light (forecast or same-weekday average, see metrics).
- **Live figures** grid: *Occupancy* `10 / 13` (→ 01a Tables), *Guests* `286`, *Per guest* `169 Kč`.
- **Shortcuts** list: *On shift · 4 staff* (→ 03 Staff), *Possible loss · 1 870 Kč* in red
  (→ 02c Discounts & losses).
- Venue switcher opens a sheet: one venue or *All venues* (not designed).
- **First release** (`MVP_SCOPE.md#data-sources-in-the-first-release`): *Open tabs* and *Occupancy*
  stay on screen as empty-state cards ("Not available from your POS"), with no value; *Tips · card
  69%* drops the card share and shows only the tips total; *On shift* is hidden; *Possible loss*
  names the components it includes. Tapping the *Occupancy* card still opens 01a on *This week*.
- API (`GET /dashboard/today`): the app draws the *Open tabs* / *Occupancy* empty states and hides
  *On shift* and the card share from `unavailable`; it shows "N venues still syncing" for
  `pendingVenueIds` and a "N bills couldn't be read" note when a `dataGaps` count isn't 0; before the
  first sync (`status NO_DATA_YET`) every figure is empty, never 0.
- **Built (phase 5, mobile):**
  - The header date is the response's business day, so after midnight it still shows the evening's
    date; the clock is the venue's time and moves each minute. *Updated 21:38* under the figures is the
    oldest last sync of the venues with data (ADR 0005).
  - The comparison pill is hidden when `comparison` or its `deltaPercent` is null; its tone follows the
    sign (0.0 is neutral).
  - Hourly chart: past hours `chartMutedStrong`, current hour `accent`, forecast hours `chartFuture`; a
    forecast without any earlier same weekday is an empty slot, never a 0 label. No value or axis labels
    (as on the canvas); the chart is one spoken node (*Revenue by hour, peak 22:00*).
  - *Possible loss* names the included components (*Includes discounts, refunds*); a component the app
    version doesn't know yet adds *and other losses* (it is in the total). An hour of an unknown kind
    keeps an empty slot; an unknown data status asks the user to update the app. It is shown without
    a chevron, and *Occupancy* is not tappable, until 02c and 01a exist.
  - `dataGaps`: one warning card (*Some bills are missing from these figures.*) with one line per count
    above 0: couldn't be read, without items (missing from product figures), may be out of date, and
    left out of paid so far because the payments don't add up (METRICS, payment mismatch).
  - The figures poll every 30 s while the screen is visible (ETag, `304` keeps them). Pull to refresh
    polls at once. A failed poll keeps the last figures with a one-line note; a failed first load shows
    the error with *Try again*. `CURRENCIES_DIFFER` / `BUSINESS_DAYS_DIFFER` say the venues can't be
    shown together.
  - No venue selected → *No venues yet*. The venue switcher button and the account avatar are not shown
    until the app loads the venue list and the profile (`GET /venues`, `GET /me`). Until then *All
    venues* also shows *No venues yet*. The 5 min interval for a closed venue waits for the API to say
    whether the venue is open.

## 01a · Tables — `AltTables` (opened from Today › Occupancy)

First release: only *This week*; the *Now* tab is not shown (`MVP_SCOPE.md`).

- Back to Today. Title **Tables**, subtitle depends on mode:
  - Now: *"10 of 13 occupied · 9 860 Kč open"*
  - This week: *"Revenue per seat, 2 to 8 Nov"*
- Segmented tabs: **Now | This week**.
- Floor plan grid: *Bar* counter spans the full row, then tables T1…T12. Each tile:
  - Now: occupied = dark tile with open-tab amount, free = light tile *"Free"*.
  - This week: heat colour by revenue per seat (6-step ramp), label like `12.4k`.
  - Selected tile has an amber outline.
- Detail panel for the selected table: title (*Table 5* / *Bar counter*), zone + seats
  (*Window · 6 seats*), three stats:
  - Now, occupied: *Open tab*, *Seated*, *Since*.
  - Now, free: *Status: Free*, *Seats*, *Last guest*.
  - This week: *Revenue*, *Per guest*, *Occupied %*.
- Insight card: *"Best corner is the window. T1, T2 and T5 bring 40% of table revenue with 32% of
  the seats."* (generated, see `docs/ai/ASSISTANT.md#generated-insights`).
- Table layout (position, zone, seats) comes from the POS where available. Manual layout editing is
  an open question.

---

## 02 · Sales — `AltSales`

- Title **Sales**, date range label (*2 to 8 Nov*), **Choose dates** button, **More** button.
- Period tabs: **Hour | Day | Week | Month** (default Week).
  - Hour = current hour (*21:00 to 22:00*), compared with the same hour last same weekday.
  - Day = current business day vs same weekday last week.
  - Week = vs last week. Month = vs previous month.
- **REVENUE** `312 480 Kč`, delta *+8.1% vs last week*.
- Stats: *Guests* `1 846`, *Avg bill* `412 Kč`, *Per guest* `169 Kč`.
- **By payment**: *Tips* total, split bar *Card · 68%* `212 490 Kč` / *Cash · 32%* `99 990 Kč`.
- Links: **Products by hour** (02a), **Hourly patterns** (02b), **Discounts & losses** (02c, shows
  the period's loss amount, e.g. `−22 410 Kč`).

## 02a · Products by hour — `AltProducts`

- Back to Sales. Subtitle: *"Thu, 5 Nov · menu synced from POS"* (one business day).
- Category chips: **All · Beer · Cocktails · Wine · Daily menu** (categories come from the POS menu).
- Hourly bar chart for the selected product, 16:00 → 02:00, value labels on bars. Happy Hour hours
  in accent, peak hour dark, rest grey. Header: product name, *n sold*, *Peak 22:00*.
- Caption: *"Happy Hour 17:00 to 19:00, by order time"* (hours are bucketed by **order time**, not
  payment time).
- Item list: name, *category · n sold*, gross revenue. Tapping a row selects it for the chart.

## 02b · Hourly patterns — `AltBusy`

- Back to Sales. Subtitle *"Guests per hour · last 8 weeks"*.
- Heatmap: rows Mon…Sun, columns 17…02 (one-hour buckets), 7-step colour ramp, legend
  *Quiet → Packed*. Each cell is a button; selected cell outlined.
- *SELECTED* card: *"Sat, 22:00 to 23:00"* and average guests.
- Patterns: *Peak* (*Sat 22:00 to 00:00*), *Quietest* (*Mon 17:00 to 19:00*).
- Staffing hint (generated): *"Thursdays after 21:00 grew 18% this month. Usually 3 staff are on
  shift then. A fourth from 21:00 would match demand."*

## 02c · Discounts & losses — `AltLoss`

- Back to Sales. Range *2 to 8 Nov*.
- *POSSIBLE LOSS* `22 410 Kč`, *+0.8 pts vs last week*, *240 records · 6.7% of revenue*.
- **By type** (colour swatch, name, meta with count, amount):
  - *Discounts* (Happy Hour, staff, comps, loyalty · 203) `−18 640`
  - *Deleted items* (removed after ordering · 31) `−2 120`
  - *Refunds* (paid, then returned · 4) `−890`
  - *Issued, unpaid* (bill closed without payment · 2) `−760`
- **Happy Hour** card: rule (*Mon to Fri · 17 to 19 · 30% off*), *Discount* `−9 870`,
  *Extra sales* `+16 170`, *Net, Kč* `+6 300`.
- Alert: *"9 of today's deleted items came from one waiter. Review with receipts."* → 03a.
- Stock corrections logged in S7 also appear here (see `docs/domain/STOCK.md`).

---

## 03 · Staff — `AltStaff`

- Title **Staff**, subtitle *"Today · hours from POS sign-in"*.
- Team totals: *HOURS* `18:40`, *TIPS* `3 940`, *GUESTS* `286`.
- **On shift now** list (header *Sales · Tips*): initials avatar, name (*Tomáš N.*), meta
  (*Bar lead · since 16:00 · 82 guests*), sales, tips. Row → 03a.
- **Earlier today**: staff who signed out (*Jan V. · Day shift · 10:00 to 16:00 · 6 h · 8 940 Kč*).

## 03a · Waiter detail — `AltStaffDetail`

- Back to Staff. Role label (*Waiter*), avatar, name, *"On shift · signed in 16:00 · 5 h 40 m"*.
- Today stats grid: *SALES*, *TIPS*, *GUESTS*, *BILLS PAID*, *OPEN TABS*, *DELETED* (deleted is
  highlighted when above usual).
- **Deleted items** list: time, item × qty, *Table 7 · bill #4821*, amount (`−320`).
  *Show all 9 with receipts* → receipt list (Receipt screen not designed).
- Context card: *"Above usual. Her typical shift has 2 to 3 deleted items. It may be a busy table
  changing orders, worth a quick check."* (Copy must be neutral, not accusatory. Use the person's
  pronouns only if known, otherwise their name.)

---

## 04 · Stock · Order plan — `StockPlan`

Stock tab has three sections (segmented): **Order plan | Idle stock | Count**, plus venue label.

- Header card: *ORDER BY MON 10:00 · DELIVERY TUE*, total `14 160 Kč`,
  *7 items · 3 suppliers · covers until Tue 17 Nov*, badge *2 at risk*.
- **Review and send to suppliers** (primary action; sending channel is an open question).
- **Buy** list (*Qty · cost*): risk dot, name, reason (*Runs out Sat · Spritz up 34%* in red when
  at risk), quantity (*6 btl*, *2 kegs*), cost. Row → S1. *"+ 3 more: tonic, oranges, Prosecco"*.
- **Don't buy this week**: *Campari 1L, 9 bottles on hand cover about 5 weeks · Skip*;
  *Blue curaçao 0.7, Not sold in 6 weeks · see idle stock · Skip*.

## S1 · Why this order — `StockItem`

- Back to order plan. *Aperol 1L*, *Spirits wholesaler · 390 Kč per bottle · case of 6*.
- *RECOMMENDED FOR TUESDAY* **Buy 6 bottles**, cost + note:
  - qty = recommended → *"one full case"*
  - qty < recommended → *"below forecast, may run out by Sunday"*
  - qty > recommended → *"n above forecast, ties up cash"*
- Stepper −/+ and **Keep in order**.
- **How we got 6** (calculation breakdown):
  - *Use until the next delivery* (Forecast, Tue 10 to Tue 17 Nov) `5.1`
  - *Safety buffer* (Saturdays vary by about 20%) `+ 0.9`
  - *On hand at delivery* (Sunday count, minus sales since) `− 0`
  - *Bottles to buy, rounded to a case* `6`
- **Used in** (recipes from *Cocktail costs.xlsx*): *Aperol Spritz · 60 ml × 78 a week* `4.7 L`,
  *Aperol Sour · 30 ml × 14 a week* `0.4 L`.
- Risk card: *"Runs out Saturday, before this delivery. About 22 Spritz sales (3 500 Kč) at risk
  until Tuesday. A 2-bottle top-up from cash and carry covers it."*

## S2 · Idle stock — `StockIdle`

- *CASH TIED UP IN SLOW STOCK* `18 400 Kč`, *"9 items with no or very slow movement for 6+ weeks ·
  at cost"*.
- Items with a suggested action, each can be applied once and shows a done text:
  - *Blue curaçao 0.7, 4 bottles · last sold 6 weeks ago, 1 280 Kč* → *Feature in a Happy Hour
    drink* → *Added to Assistant as a menu idea*
  - *Jägermeister 1L* → *Stop reordering* → *Removed from future orders*
  - *Prosecco rosé* → *Ask supplier about a return* → *Return request drafted*
  - *Craft IPA 0.33* → *Bundle with a burger* → *Bundle idea saved*
- Footer: *"The order plan won't suggest these again until they start selling."*

## S3 · Count — `StockCount`

> Counts are on demand, not weekly (see `docs/domain/STOCK.md#counts-s3`). The design's
> *Sunday count* / *counted every week* copy is example text: replace it with the chosen scope
> and *last counted* information.

- *Sunday count · Spirits shelf*, progress *12 of 38*, *"About 6 minutes left. Only items that move
  get counted every week."*
- Current item: *BOTTLES ON SHELF · Aperol 1L · Expected 1.5*.
- Input: whole bottles stepper (−/+), fraction buttons **Full · ¼ · ½ · ¾**.
- Variance line: *"Matches what we expected"* or *"−0.5 bottle vs expected · worth a look"*
  (warning style when |variance| ≥ 0.5).
- *Next: Beefeater Gin 1L*.
- Footer: *"Expected = last count + deliveries − sales, using your recipes. Counts keep the order
  plan honest."*

## S4 · Storage locations — `StockLocations`

- *Storage · 3 locations · 186 400 Kč on hand*.
- Locations: *Main bar (Front · 64 items · 4 below par)* `41 200 Kč`; *Back storage (Main storage ·
  112 items, Receiving)* `128 900 Kč`; *Van (Events · 18 items)* `16 300 Kč`.
- *TRANSFER BEFORE TONIGHT*: *"Main bar is below par on 4 items. Back storage has all of them."*
  chips (*Aperol 1L · 2*, *Prosecco · 6*, *Tonic 0.2 · 24*, *Limes · 1 box*), **Start pull list** → S5.
- Order suggestion: *"Beefeater Gin 1L, 2 bottles across all locations · reorder at 4 · nothing to
  transfer" · Order*.
- Rule: *"Transfer first. The order plan only asks you to buy when the whole venue runs low."*

## S5 · Pull list (transfer) — `StockTransfer`

- *Pull list · Before opening · Thu 19 Nov*, *FROM Back storage → TO Main bar*.
- Checklist: name, reason (*Shelf 1.25 · par 3*), quantity. Progress bar.
- CTA: *"Confirm n of m moved"* / *"Confirm transfer"* when all are checked.
- Footer: *"Logged as Ali at 17:42. A transfer moves stock between locations, so the venue total
  does not change. Unchecked items stay on tomorrow's list."*

## S6 · Scan invoice — `StockInvoice`

- *Delivery · Scanned by Ali at 14:05 · waiting for your approval*.
- Invoice photo, supplier (*Nápoje Novák s.r.o.*), *Invoice 2026-1184 · 17 Nov · 9 lines*.
- *Into Back storage · change* (target location).
- Lines: raw OCR text (`BEEF.GIN 1L 40% 6KS`) → matched item (*Beefeater Gin 1L · 6 btl*), note,
  price. Notes: *Price up 8% since last delivery* (warning), *Remembered match*,
  *New name from this supplier* + **Match** button (unmatched), *1 crate = 24 bottles* (unit
  conversion). *"+ 5 more lines, matched from earlier deliveries"*.
- *Total, matches invoice* `9 870 Kč`.
- CTA: *"Match 1 line to approve"* (disabled) → *"Approve and add 9 lines to stock"*.

## S7 · Recipe & corrections — `StockRecipe`

- *Negroni · Recipe · Cocktails · sells at 185 Kč*, *MARGIN 78%*.
- Ingredients: name, quantity, cost (*Beefeater Gin · 30 ml · 18.0 Kč* highlighted if price
  changed). *Cost per drink* `40.7 Kč`.
- Price alert: *"Gin went up 8% on the 17 Nov invoice. Each Negroni now costs 1.3 Kč more."*
- *THIS WEEK · 46 SOLD IN STORYOUS*: *"1.38 L gin, 1.38 L Campari, 1.38 L vermouth taken from Main
  bar"* (theoretical usage).
- **Log 1 Negroni that wasn't sold**: reasons **Spilled · Made wrong, remade · Comp · Welcome drink**.
  Result line: *"Will log as "Made wrong, remade" under your name. Shows in Discounts & losses."*
  Note: *"Nothing is deleted. Stock is adjusted with a reason."*

---

## 05 · Assistant — `AltInsights`

- Header: assistant icon, *Main bar assistant*, *"Storyous live · 5 documents"*, **Knowledge
  sources** button → 05a.
- Conversation:
  - Timestamp (*Today, 21:40*).
  - **TONIGHT'S BRIEFING** card: *"48 320 Kč so far, 12% above last Friday. At this pace Aperol runs
    out around 23:40."*
  - User bubble, assistant answer with prose + inline data table + follow-up question.
  - Source chips under each answer: *Storyous · sales*, *Cocktail costs.xlsx*.
- Suggested prompts (horizontal chips): *Yes, compare 4 weeks*, *Closing checklist*,
  *Why was Tuesday slow?*
- Composer: attach document button, input (*"Ask about sales, staff, recipes…"*), send.
- Suggestions (from the earlier *Insights* screen) are part of the assistant: see
  `docs/ai/ASSISTANT.md#suggestions`.

## 05a · Knowledge — `AltKnowledge`

- Back to Assistant. *"Knowledge · What the assistant can read"*, **Upload**.
- *LIVE DATA*: *Storyous POS · Sales, staff, tables · synced 2 min ago · Live*.
- *DOCUMENTS* (header *Who can ask about it*): type badge (PDF/DOC/XLS), name, meta
  (*24 pages · ready* / *Reading the document…*), access badge **All staff** or **Owner**.
- Privacy note: *"Private to Main bar. Answers always name their source. Staff only get answers from
  documents marked All staff."*
