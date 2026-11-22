# Database schema (draft)

> **Status: accepted** (2026-11-20) for `platform`, `account`, `pos` and `reporting`. `stock` follows
> after open questions P13/P14. Flyway migrations are written from this file; keep both in sync.

Follows [ADR 0002](adr/0002_POSTGRES_FLYWAY_JOOQ.md) (PostgreSQL + Flyway + jOOQ),
[ADR 0013](adr/0013_BACKEND_MODULE_STRUCTURE.md) (schema per module, no cross-schema reads) and
[ADR 0010](adr/0010_API_DESIGN.md) (UUIDv7, money). Field sources: `docs/integrations/STORYOUS.md`.

## Conventions

- PostgreSQL 18. One schema per module, plus `platform` for infrastructure tables.
- snake_case names. Primary keys `id uuid primary key default uuidv7()`.
- Every table has `created_at timestamptz not null default now()`; mutable tables also
  `updated_at timestamptz not null default now()` (the application sets it on update). Editable-by-user tables have `version int not null default 0`
  (optimistic locking, ADR 0010).
- Money: `*_minor bigint` + `currency char(3)` (or the venue's currency where obvious).
  Decimal quantities: `numeric(12,3)`. Rates: `numeric(6,4)`.
- Our own small value sets are `text` + `CHECK` (no PostgreSQL enum types). Change a CHECK with
  `ADD CONSTRAINT … NOT VALID` then `VALIDATE CONSTRAINT` to avoid long locks.
- Raw values coming from a POS (payment method, discount type, …) are plain `text` **without** a
  CHECK, so a new POS value never breaks the sync; map them to our own CHECKed columns
  (e.g. `method_kind`).
- Venue-scoped tables have `venue_id uuid not null references account.venue(id)` and an index
  starting with `venue_id`. Cross-module foreign keys only to `account.venue` and
  `account.app_user`.
- External (POS) IDs are kept as `external_id text` with a unique key per venue/connection.
- No soft deletes by default. Use a status or `archived_at` where the domain needs it.
- Flyway: one history table in `platform` (`spring.flyway.default-schema`); migrations in
  `backend/src/main/resources/db/migration`, named `V<NNN>__<module>_<what>.sql` (`V001__init.sql` is an
  empty baseline, then `V002__account_init.sql`, `V003__pos_init.sql`, `V004__reporting_init.sql`).
  Each migration creates its module schema and schema-qualifies every object.
- Extensions are installed in `public` (`create extension … with schema public`) and referenced
  qualified (`public.citext`), so they work whatever the connection's `search_path`.

## `platform` (infrastructure, owned by `common`)

Each table is created by the migration of the feature that needs it (event registry, db-scheduler,
idempotency filter; see `docs/ROADMAP.md`), because the first two take their DDL from the library
version in use.

| Table | Purpose / key columns |
|-------|------------------------|
| `event_publication` | Spring Modulith event publication registry, v2 structure (DDL from Spring Modulith 2.2 JDBC, `V005`). `spring.modulith.events.jdbc.schema=platform`, schema initialization disabled (Flyway owns the DDL). db-scheduler jobs purge completed publications older than 7 days (hourly) and resubmit failed ones older than 10 minutes (every 5 minutes). |
| `scheduled_tasks` | db-scheduler task table (DDL from db-scheduler 17.1, `V005`). `db-scheduler.table-name=platform.scheduled_tasks`. |
| `idempotency_record` | `user_id → account.app_user` (cascade), `key uuid`, `method`, `path`, `request_hash bytea` (SHA-256 of method, path, query and body; multipart: of the parts), `claim_id uuid` (new per claim; only the claiming request may complete or release the row), `status (IN_PROGRESS\|COMPLETED)`, `response_status`, `response_headers jsonb` (`Content-Type`, `Location`), `response_body text` (not `jsonb`: replays send the original bytes), `created_at`, `expires_at` (24 h). PK `(user_id, key)`, index on `expires_at` (`V006`). An `IN_PROGRESS` row older than 5 minutes is taken over by a retry of the same request (crashed instance). A db-scheduler job deletes expired rows hourly. |

## `account`

| Table | Key columns | Notes |
|-------|-------------|-------|
| `organization` | `id`, `name`, `version`, `deletion_due_at` | The customer business. `deletion_due_at` is set when its last owner deletes their account (see below). |
| `app_user` | `id`, `email citext unique`, `email_verified_at`, `display_name`, `locale` | |
| `user_identity` | `id`, `user_id → app_user`, `provider (APPLE\|GOOGLE\|EMAIL)`, `subject`, `email` | Unique `(provider, subject)`. Apple private-relay emails stored as given. |
| `email_login_code` | `id`, `email`, `code_hash`, `expires_at`, `attempts`, `consumed_at`, `requested_ip` | 10 min, max 5 attempts (ADR 0004). Index `(email, created_at desc)`. Purged after 1 day. |
| `refresh_token` | `id`, `user_id`, `family_id`, `token_hash unique`, `expires_at`, `used_at`, `revoked_at`, `replaced_by_id`, `device_name` | Rotation + reuse detection by family. Expired rows are deleted hourly. |
| `membership` | `id`, `organization_id`, `user_id`, `role (OWNER\|STAFF)` | Unique `(organization_id, user_id)`. First release: OWNER only. |
| `venue` | `id`, `organization_id`, `name`, `address_line`, `city`, `time_zone` (default `Europe/Prague`), `currency char(3)`, `business_day_start time` (default `05:00`), `status (ACTIVE\|DISABLED)`, `version` | Created when the POS sync discovers a place. |
| `followed_venue` | `user_id`, `venue_id` | PK `(user_id, venue_id)`. Venues shown in Today/Sales (A5). |

Account deletion (`DELETE /me`, App Store 5.1.1(v), GDPR art. 17):

- The `app_user` and its personal data are deleted **immediately** (cascades identities, tokens,
  memberships, followed venues). Deactivation alone is not enough.
- If the user was the **last owner** of an organization, the organization is marked for deletion
  and all its data (venues, POS read model incl. staff names, reporting, stock) is deleted by a
  background job after **30 days** (inside GDPR's one-month window). During those 30 days an
  accidental deletion can be reversed through support. Legal wording to be confirmed. The mark is
  `organization.deletion_due_at` (V007). A support reversal clears `deletion_due_at` (and restores an
  owner membership) before it passes.
- The purge job (`account/application/PurgeDueOrganizations`, hourly) deletes each due organization
  in its own transaction: it re-checks the mark under a row lock, locks the venues (a concurrent sync
  insert waits and then fails instead of blocking the venue delete), publishes the synchronous
  `OrganizationPurging` event, in which every module deletes its rows of the organization and its
  venues (`pos`: the whole read model incl. connections, their venue links and staff; `reporting`: all rollups), then
  deletes the venues (followed venues cascade) and the organization (remaining memberships cascade).
  Other members stay users. A failing organization is rolled back whole, stays marked and is tried
  again next hour. A module that adds a venue- or organization-scoped table deletes it in its
  listener; `OrganizationPurgeTests` fails until the table is seeded there. Stock history needs a
  retention decision under product rule 4 and GDPR when the `stock` schema is added.
- History and audit tables that record who did something (stock movements, counts, receipts,
  corrections) reference `app_user` with **`on delete set null`, never `cascade`**: deleting a user
  must not delete stock history (product rule 4). Only the user's own data (identities, tokens,
  memberships, followed venues, idempotency records) cascades.

## `pos` (read model of the POS, written only by the sync)

| Table | Key columns | Notes |
|-------|-------------|-------|
| `pos_connection` | `id`, `organization_id`, `provider (STORYOUS)`, `external_merchant_id`, `status (PENDING\|ACTIVE\|ERROR\|REVOKED)`, `last_error`, `last_synced_at` | Storyous uses **our app's** client credentials (server config), so no per-merchant secret is stored. Unique `(provider, external_merchant_id)`. |
| `venue_link` | `venue_id` (PK), `connection_id`, `external_place_id`, `active` | Unique `(connection_id, external_place_id)`. `active = false` (V008) when the POS reports the place disabled/hidden or stops reporting it: the sync skips the venue, the venue itself stays (pending owner confirmation, `STORYOUS.md`). |
| `sync_cursor` | `venue_id`, `resource (BILLS\|MENU\|DESKS)`, `cursor text`, `last_success_at`, `last_error` | PK `(venue_id, resource)`. Bills cursor = last `_lastModifiedAt`. |
| `sync_job` | `id`, `connection_id`, `kind (INITIAL\|INCREMENTAL\|BACKFILL)`, `status (QUEUED\|RUNNING\|SUCCEEDED\|FAILED)`, `started_at`, `finished_at`, `steps jsonb` | Steps = `[{name, state, progress, countLabel}]` for A4. |
| `category` | `id`, `venue_id`, `external_id`, `name`, `parent_id` | Unique `(venue_id, external_id)`. Storyous menus are merchant-level; we keep a copy per venue for simple queries. |
| `product` | `id`, `venue_id`, `external_id`, `category_id`, `name`, `measure`, `ean`, `vat_rate`, `price_minor`, `active` | Unique `(venue_id, external_id)`. |
| `section` | `id`, `venue_id`, `external_id`, `name`, `color`, `removed` | Storyous desk sections = zones. |
| `desk` | `id`, `venue_id`, `external_id`, `section_id`, `name`, `code`, `is_virtual`, `removed` | No seats in Storyous (see `reporting.desk_capacity`). |
| `staff_member` | `id`, `connection_id`, `external_id` (personId), `full_name`, `user_name` | Unique `(connection_id, external_id)`. Merchant-level (a person can work in several venues). Filled from bill `createdBy`/`paidBy`. |
| `bill` | `id`, `venue_id`, `external_id` (billId), `session_started_at`, `pos_created_at`, `paid_at`, `fiscalized_at`, `business_day date`, `total_minor`, `total_without_tax_minor`, `discount_minor`, `rounding_minor`, `tips_minor`, `currency`, `refunded`, `deleted`, `desk_id`, `external_desk_id` (V009; fills `desk_id` once the desk is synced), `items_unreadable` (V009; stored from the listing header, no lines, detail quarantined), `created_by_staff_id`, `paid_by_staff_id`, `guest_count`, `external_modified_at` | Unique `(venue_id, external_id)`. Indexes `(venue_id, business_day)`, `(venue_id, paid_at)`, `(venue_id, external_modified_at)`. `business_day` = the paid business day, from `paid_at`, venue time zone and cut-off (owner decision 2026-11-22). |
| `bill_item` | `id`, `bill_id → bill (cascade)`, `venue_id`, `line_no`, `product_id`, `external_product_id`, `name`, `quantity`, `measure`, `unit_price_minor`, `vat_rate`, `discount_minor`, `total_minor` | Storyous items have no stable ID: when a bill changes, its items are **replaced** in the same transaction. Skip entirely if `external_modified_at` is unchanged. Index `(venue_id, product_id)`. |
| `bill_item_discount` | `id`, `bill_item_id (cascade)`, `discount_type`, `amount_type`, `percent_value`, `absolute_minor`, `original_price_minor`, `note` | |
| `payment` | `id`, `bill_id (cascade)`, `method_raw`, `method_kind (CARD\|CASH\|OTHER)`, `amount_minor` | Mapping raw → kind per provider. |
| `raw_payload` | `id`, `venue_id`, `resource`, `external_id`, `payload jsonb`, `fetched_at` | Debug/reprocessing, kept 30 days. |
| `bill_quarantine` | `venue_id`, `external_id`, `reason`, `first_seen_at`, `last_attempt_at`, `attempts` | V009. PK `(venue_id, external_id)`, index `(venue_id, last_attempt_at)`. Bills the sync couldn't read (no detail, or a value it can't store exactly); retried every run for 3 attempts, then hourly; removed once read. Their count backs a "N bills couldn't be read" note (rule 2). Deleted with the organization. |

POS capabilities (guest count, open bills, item voids, desks, attendance) are **constants per
provider in code**, not a table; the API exposes them per venue.

**To verify with real data:** units of Storyous item `price` (the docs example mixes integer item
prices with a decimal-string bill total) and the meaning of `sessionCreated` vs `createdAt`.

## `reporting` (derived; rebuildable from `pos`)

Own rollup tables, not materialized views: PostgreSQL materialized views recompute everything on
refresh, while these tables are refreshed per `(venue_id, business_day)` after each `BillsSynced`
event. A rebuild job recomputes any range from `pos`. `product_id`, `staff_id` and `desk_id` point to
`pos` rows without foreign keys (cross-module rule); amounts are in the venue's currency. Hours are local clock hours; on the DST
fall-back night the repeated hour is merged into one row.

| Table | Grain (PK) | Columns | Basis |
|-------|-----------|---------|-------|
| `hourly_sales` | `venue_id, business_day, hour` | `revenue_minor`, `revenue_without_tax_minor`, `bills`, `guests`, `tips_minor`, `discount_minor`, `card_minor`, `cash_minor`, `other_minor`, `refunded_minor`, `deleted_bills`, `deleted_minor`, `bills_missing_net`, `bills_payment_mismatch` (V010) | Local hour of `paid_at`. `revenue_minor` = payments without tips, refunded bills included, bills whose payments don't add up left out (counted in `bills_payment_mismatch`); the net figure is incomplete while `bills_missing_net` > 0 |
| `hourly_activity` | `venue_id, business_day, hour` | `sessions`, `guests` | Local hour of `session_started_at` (hourly patterns, 02b) |
| `product_hourly_sales` | `venue_id, business_day, hour, product_id` | `quantity`, `gross_minor`, `discount_minor` | Session hour (no per-item order time) |
| `staff_daily_sales` | `venue_id, business_day, staff_id` | `bills`, `revenue_minor`, `guests`, `discount_minor`, `deleted_bills` (by `created_by`); `tips_minor` (by `paid_by`) | Sales to the person who **opened** the bill, tips to the person who **closed** it (answers M6). Both IDs stay on `pos.bill`, so a per-venue setting can be added later. |
| `desk_daily_sales` | `venue_id, business_day, desk_id` | `bills`, `revenue_minor`, `guests`, `occupied_minutes` | `occupied_minutes` = Σ(paid_at − session_started_at) |
| `discount_daily` | `venue_id, business_day, discount_type` | `records`, `amount_minor` | |
| `desk_capacity` | `venue_id, desk_id` | `estimated`, `override`, `updated_at`, `version` | Estimated = highest guest count seen in 8 weeks; owner can override (labelled "estimated") |
| `rollup_state` | `venue_id, business_day` | `refreshed_at`, `source_max_modified_at`, `rollup_version` (V010) | Tracks what is up to date; the rebuild job recomputes days missing here or below `reporting/domain/RollupVersion.CURRENT` |

Sizing: a busy bar has ~300 bills/day → ~100k bills/year per venue. No partitioning for now;
revisit at millions of rows (range partitioning by `business_day`).

## `stock`

Pending P13/P14. Model in `docs/domain/MODEL.md` and `docs/domain/STOCK.md` (append-only
movements, receipts, counts, transfers, recipes, locations).
