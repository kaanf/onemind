# Domain model

Logical model. It is a **draft**: the persistence design (tables, indexes, partitioning) will be
finalised in the architecture discussion. Field lists show what the screens need, not a final
schema.

## Bounded contexts

| Context        | Owns                                                                 | Source of data            |
|----------------|----------------------------------------------------------------------|---------------------------|
| Identity       | Organization, User, membership, app roles, sessions                   | Our app                   |
| POS integration| PosConnection, SyncJob, raw POS payloads, ID mapping                   | Storyous (read-only)      |
| Sales          | Venue, BusinessDay, Bill, BillItem, Payment, Discount, DeletedItem, Refund | POS (synced, immutable) |
| Floor          | Table, TableZone, table sessions                                      | POS (+ optional manual layout) |
| Staff          | StaffMember, Shift                                                     | POS                       |
| Menu           | Product, Category, HappyHourRule                                       | POS                       |
| Stock          | StockItem, Supplier, Recipe, StockLocation, StockMovement, StockCount, Transfer, PullList, Correction, OrderPlan, Delivery, SupplierInvoice | Our app (user input + computed) |
| Assistant      | Conversation, Message, Citation, Briefing, Suggestion, Insight, KnowledgeDocument | Our app + LLM        |

Sales, Floor, Staff and Menu are **read models of the POS**: never edited by users. Stock and
Assistant hold **our own** data.

## Entities

```mermaid
erDiagram
  Organization ||--o{ Venue : has
  Organization ||--o{ Membership : has
  User ||--o{ Membership : has
  Organization ||--o| PosConnection : has
  PosConnection ||--o{ SyncJob : runs

  Venue ||--o{ Table : has
  Venue ||--o{ StaffMember : employs
  Venue ||--o{ Product : sells
  Venue ||--o{ HappyHourRule : defines
  Venue ||--o{ Bill : records
  Bill ||--o{ BillItem : contains
  Bill ||--o{ Payment : paidBy
  Bill }o--o| Table : at
  Bill }o--o| StaffMember : servedBy
  BillItem }o--|| Product : of
  BillItem ||--o{ Discount : has
  Bill ||--o{ DeletedItem : had
  StaffMember ||--o{ Shift : works

  Venue ||--o{ StockLocation : has
  Venue ||--o{ StockItem : stocks
  Supplier ||--o{ StockItem : supplies
  Product ||--o| Recipe : madeBy
  Recipe ||--o{ RecipeIngredient : uses
  RecipeIngredient }o--|| StockItem : of
  StockItem ||--o{ StockMovement : moves
  StockLocation ||--o{ StockMovement : at
  StockLocation ||--o{ ParLevel : targets
  Venue ||--o{ StockCount : counts
  Venue ||--o{ OrderPlan : plans
  OrderPlan ||--o{ OrderPlanLine : contains
  Supplier ||--o{ SupplierInvoice : sends
  SupplierInvoice ||--o{ InvoiceLine : contains
  Supplier ||--o{ SupplierItemAlias : remembers

  Venue ||--o{ KnowledgeDocument : owns
  Venue ||--o{ Conversation : has
  Conversation ||--o{ Message : contains
  Message ||--o{ Citation : cites
  Venue ||--o{ Suggestion : receives
```

## Key fields

### Identity

- **Organization**: `id`, `name`, `createdAt`.
- **User**: `id`, `email`, `displayName`, `authProviders[] (APPLE|GOOGLE|EMAIL)`, `locale`.
- **Membership**: `userId`, `organizationId`, `role (OWNER|STAFF)`, `venueIds[]` (venues the user
  follows / may access), `staffMemberId?` (link to POS staff member, for staff users).

### POS integration

- **PosConnection**: `id`, `organizationId`, `provider (STORYOUS)`, `status
  (PENDING|ACTIVE|ERROR|REVOKED)`, encrypted credentials, `lastSyncedAt`.
- **SyncJob**: `id`, `connectionId`, `kind (INITIAL|INCREMENTAL|BACKFILL)`, `steps[]` with
  `name`, `state (WAITING|RUNNING|DONE|FAILED)`, `progress%`, `countLabel`.
- **External ID mapping**: every synced entity keeps `externalId` + `provider`; upserts are
  idempotent by `(provider, externalId)`.

### Sales (read model)

- **Venue**: `id`, `organizationId`, `name`, `district`, `timeZone`, `currency`,
  `businessDayStart` (e.g. `05:00` cut-off, see metrics), `externalId`.
- **Bill**: `id`, `venueId`, `number`, `tableId?`, `staffMemberId?`, `openedAt`, `closedAt?`,
  `status (OPEN|PAID|REFUNDED|UNPAID_CLOSED)`, `guestCount`, `total`, `businessDay`.
- **BillItem**: `id`, `billId`, `productId`, `qty`, `unitPrice`, `discountAmount`, `orderedAt`.
- **Payment**: `billId`, `method (CARD|CASH|OTHER)`, `amount`, `tip`, `paidAt`.
- **Discount**: `billItemId|billId`, `type (HAPPY_HOUR|STAFF|COMP|LOYALTY|OTHER)`, `amount`,
  `ruleId?`.
- **DeletedItem**: `billId`, `productId`, `qty`, `amount`, `deletedAt`, `staffMemberId`.
- **Refund**: `billId`, `amount`, `at`, `staffMemberId?`.

### Floor, staff, menu

- **Table**: `id`, `venueId`, `name (T1|Bar)`, `zone`, `seats`, `layout {row, col, span}?`.
- **StaffMember**: `id`, `venueId`, `name`, `initials`, `role (BAR_LEAD|BARTENDER|WAITER|OTHER)`.
- **Shift**: `staffMemberId`, `signedInAt`, `signedOutAt?`.
- **Product**: `id`, `venueId`, `name`, `categoryId`, `price`, `vatRate`, `active`.
- **HappyHourRule**: `venueId`, `days[]`, `from`, `to`, `discountPercent`, `categoryIds[]`.

### Stock (our data)

- **StockItem**: `id`, `venueId`, `name`, `unit (BOTTLE|KEG|PIECE|BOX|L|KG)`, `unitSize`
  (e.g. 1 L), `caseSize` (6), `supplierId`, `unitCost`, `lastCountedAt`,
  `excludedFromOrdering` (idle "stop reordering").
- **StockLocation**: `id`, `venueId`, `name` (user-defined: *Bar*, *Back storage*, *Home*…),
  `isReceiving`, `isSalesLocation`, `archivedAt?`, `sortOrder`.
- **ParLevel**: `locationId`, `stockItemId`, `par`.
- **StockMovement** (append-only): `id`, `stockItemId`, `locationId`, `qtyDelta`, `type
  (DELIVERY|SALE_USAGE|TRANSFER_OUT|TRANSFER_IN|COUNT_ADJUSTMENT|CORRECTION)`, `reason?`,
  `refId` (bill/invoice/transfer/count id), `createdBy`, `createdAt`.
- **StockCount**: `id`, `venueId`, `locationId?`, `name`, `status`, `lines[] {stockItemId, expected,
  counted, variance}`.
- **Transfer / PullList**: `fromLocationId`, `toLocationId`, `date`, `lines[] {stockItemId, qty,
  reason, done}`, `confirmedBy`, `confirmedAt`.
- **StockCorrection**: `productId|stockItemId`, `qty`, `reason (SPILLED|REMADE|COMP|WELCOME)`,
  `createdBy`, `createdAt` → creates movements + appears in losses.
- **Recipe**: `productId`, `ingredients[] {stockItemId, qty, unit}`, `source (MANUAL|IMPORT)`,
  `sourceDocumentId?`.
- **Supplier**: `id`, `venueId|organizationId`, `name`, `orderDeadline (weekday+time)`,
  `deliveryDays[]`, `leadTime`.
- **OrderPlan**: `venueId`, `supplierId?`, `orderBy`, `deliveryAt`, `coversUntil`, `lines[]
  {stockItemId, recommendedQty, chosenQty, cost, reason, atRisk, breakdown{forecast, buffer,
  onHand, rounded}}`, `skipped[] {stockItemId, reason}`, `status (DRAFT|SENT)`.
- **SupplierInvoice**: `id`, `supplierId`, `number`, `date`, `imageAssetId`, `targetLocationId`,
  `status (SCANNED|NEEDS_MATCH|READY|APPROVED)`, `scannedBy`, `approvedBy?`, `total`,
  `lines[] {rawText, stockItemId?, qty, unitConversion?, price, priceChangePct?, matchSource
  (ALIAS|SUGGESTED|MANUAL)}`.
- **SupplierItemAlias**: `supplierId`, `rawName`, `stockItemId`, `unitConversion`.
- **IdleStockAction**: `stockItemId`, `action (MENU_IDEA|STOP_REORDER|RETURN_REQUEST|BUNDLE_IDEA)`,
  `createdBy`, `createdAt`.

### Assistant

- **KnowledgeDocument**: `id`, `venueId`, `name`, `type (PDF|DOC|XLS|…)`, `access
  (ALL_STAFF|OWNER_ONLY)`, `status (UPLOADED|PROCESSING|READY|FAILED)`, `meta (pages|rows)`.
- **Conversation / Message**: `role (USER|ASSISTANT)`, `content`, structured blocks (table rows),
  `citations[]`, `suggestedReplies[]`.
- **Citation**: `kind (POS_DATASET|DOCUMENT)`, `label` (*Storyous · sales*, *Cocktail costs.xlsx*),
  `documentId?`.
- **Suggestion**: `id`, `venueId`, `type (HAPPY_HOUR|CONTROL|TABLES|STAFFING|STOCK|MENU)`,
  `title`, `why`, `impactLabel`, `cta`, `status (OPEN|APPLIED|DISMISSED)`, `evidence`.
- **Briefing / Insight**: `venueId`, `scope (screen)`, `text`, `generatedAt`, `inputsHash`.

## Invariants

- Read-model entities are only written by the sync. User actions never modify them.
- Stock quantities are derived from movements. `onHand` may be cached but must equal the sum of
  movements since the last count adjustment.
- A transfer creates two movements with equal and opposite quantities.
- An invoice adds stock only on `APPROVED`, and only an owner may approve.
- Every row in venue-scoped tables has `venueId`; every query filters by it and by the user's
  membership.
