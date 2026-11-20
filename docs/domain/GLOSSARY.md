# Glossary (ubiquitous language)

Use these terms, and their code names, in code, API, DB and UI copy. Do not introduce synonyms.

| Term (UI)              | Code name            | Meaning |
|------------------------|----------------------|---------|
| Organization           | `Organization`       | The customer account (owner's business). Owns venues, users, documents. |
| Venue                  | `Venue`              | One bar/restaurant location (*Main bar*, *Second venue*). Maps to a Storyous place. All data is venue-scoped. |
| All venues             | `VenueScope.All`     | Aggregate view across the venues the user follows. |
| POS                    | `Pos`                | Point-of-sale system. Currently only Storyous. |
| POS connection         | `PosConnection`      | Authorised, read-only link to a Storyous account (tokens, status, last sync). |
| Sync                   | `SyncJob`            | Import of data from the POS. *First sync* = initial import incl. 90 days of history. |
| Business day           | `BusinessDay`        | The operating day of a venue, which may cross midnight (e.g. 16:00 to 03:00). "Today" = current business day. |
| Bill                   | `Bill`               | A POS receipt/check. Has items, table, waiter, payments. Identified by bill number (*#4821*). |
| Open tab               | `OpenTab`            | A bill not yet paid. *Open tabs* = sum of open bills. |
| Paid so far            | `paidRevenue`        | Revenue from bills paid in the current business day. |
| Bill item              | `BillItem`           | One product line on a bill (product, qty, unit price, discount, order time). |
| Order time             | `orderedAt`          | When an item was ordered. Hourly product charts bucket by order time. |
| Deleted item           | `DeletedItem`        | An item removed from a bill after ordering (storno). Counts as possible loss. |
| Refund                 | `Refund`             | Paid, then returned. |
| Issued, unpaid         | `UnpaidBill`         | Bill closed without payment. |
| Discount               | `Discount`           | Price reduction on an item or bill. Types: Happy Hour, staff, comp, loyalty. |
| Comp                   | `Comp`               | Item given for free (on the house). |
| Happy Hour             | `HappyHourRule`      | Time-based discount rule from the POS (*Mon to Fri · 17 to 19 · 30% off cocktails*). |
| Possible loss          | `possibleLoss`       | Discounts + deleted items + refunds + issued-unpaid (+ logged stock corrections). "Possible" because not every one is a real loss. |
| Guest                  | `guestCount`         | Number of guests (covers) on bills, as recorded by the POS. |
| Per guest              | `revenuePerGuest`    | Revenue ÷ guests. |
| Avg bill               | `averageBill`        | Revenue ÷ paid bills. |
| Tips                   | `tips`               | Tips recorded by the POS, split card/cash. |
| Staff member           | `StaffMember`        | A person in the POS (waiter, bartender, bar lead). Not necessarily an app user. |
| Role (staff)           | `StaffRole`          | POS role: *Bar lead*, *Bartender*, *Waiter*. |
| Shift                  | `Shift`              | Sign-in to sign-out period of a staff member, from POS sign-ins. |
| On shift               | `onShift`            | Signed in now. |
| User                   | `User`               | A person who logs into the app. |
| App role               | `AppRole`            | `OWNER` or `STAFF` (see overview). |
| Table                  | `Table`              | A seating unit (*T1*… or *Bar counter*), with seats and zone. |
| Zone                   | `TableZone`          | Area of the floor (*Window, Middle, Booth, Back, Bar counter*). |
| Occupancy              | `occupancy`          | Occupied tables / total tables (now), or % time occupied (period). |
| Revenue per seat       | `revenuePerSeat`     | Table revenue ÷ seats, over a period. |
| Product                | `Product`            | Sellable menu item from the POS (*Aperol Spritz*). Has category and price. |
| Category               | `Category`           | POS menu category (*Beer, Cocktails, Wine, Daily menu*). |
| Stock item             | `StockItem`          | Something we hold in stock (*Aperol 1L bottle*). Has unit, case size, supplier, cost. Not the same as a product. |
| Recipe                 | `Recipe`             | How a product consumes stock items (*Negroni = 30 ml gin + 30 ml Campari + …*). |
| Ingredient             | `RecipeIngredient`   | One line of a recipe (stock item + quantity). |
| Cost per drink         | `costPerServing`     | Sum of ingredient costs for one serving. |
| Margin                 | `margin`             | (price − cost) ÷ price. VAT handling: see metrics. |
| Location               | `StockLocation`      | Physical place stock is kept (*Main bar*, *Back storage*, *Van*). One is the receiving location. |
| Receiving location     | `isReceiving`        | Where deliveries land by default. |
| Par level              | `parLevel`           | Target quantity of an item at a location (e.g. *par 3*). Below par → transfer or order. |
| Reorder point          | `reorderPoint`       | Venue-wide quantity at which we buy (*reorder at 4*). |
| On hand                | `onHand`             | Current estimated quantity (last count + movements). |
| Expected               | `expectedQty`        | What we expect at count time: last count + deliveries − theoretical usage ± transfers ± corrections. |
| Count                  | `StockCount`         | A physical count session (*Sunday count · Spirits shelf*). |
| Variance               | `variance`           | Counted − expected. |
| Movement               | `StockMovement`      | Any change to stock: delivery, sale usage, transfer, count adjustment, correction. Append-only. |
| Transfer               | `Transfer`           | Moving stock between locations. Venue total unchanged. |
| Pull list              | `PullList`           | Checklist of transfers to do before opening. Unchecked items roll over to tomorrow. |
| Correction             | `StockCorrection`    | Logged unsold usage with a reason: *Spilled, Made wrong/remade, Comp, Welcome drink*. Shows in Discounts & losses. |
| Supplier               | `Supplier`           | Who we buy from. Has order deadline and delivery days. |
| Order plan             | `OrderPlan`          | Recommended purchases for the next delivery window, per supplier. |
| Order line             | `OrderPlanLine`      | One recommended item + qty + reason + risk flag. |
| At risk                | `stockoutRisk`       | Item forecast to run out before the next delivery. |
| Safety buffer          | `safetyBuffer`       | Extra quantity covering demand variability. |
| Idle stock             | `IdleStock`          | Items with no or very slow movement for 6+ weeks; value at cost = cash tied up. |
| Delivery / invoice     | `Delivery`, `SupplierInvoice` | Goods received with a supplier invoice. Scanned → matched → approved → stock movement. |
| Invoice line           | `InvoiceLine`        | One raw line from OCR + its matched stock item, qty, price. |
| Supplier alias         | `SupplierItemAlias`  | Remembered mapping from a supplier's raw line name to our stock item. |
| Assistant              | `Assistant`          | The AI chat for a venue. |
| Briefing               | `Briefing`           | Short generated summary of tonight shown at the top of the assistant. |
| Suggestion             | `Suggestion`         | Actionable, explained recommendation (*Extend Monday Happy Hour to 19:30*). Apply / dismiss. |
| Insight                | `Insight`            | Short generated observation attached to a screen (*Best corner is the window*). |
| Knowledge document     | `KnowledgeDocument`  | Uploaded file the assistant can read (PDF, DOC, XLS). |
| Document access        | `DocumentAccess`     | `ALL_STAFF` or `OWNER_ONLY`. |
| Source                 | `Citation`           | Reference attached to an assistant answer (POS dataset or document). |
