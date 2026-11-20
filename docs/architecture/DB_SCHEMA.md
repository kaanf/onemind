# Database schema (draft)

> **Status: accepted** (2026-11-20) for `platform`, `account`, `pos` and `reporting`. `stock` follows
> after open questions P13/P14. Flyway migrations are written from this file; keep both in sync.

Follows [ADR 0002](adr/0002_POSTGRES_FLYWAY_JOOQ.md) (PostgreSQL + Flyway + jOOQ),
[ADR 0009](adr/0009_BACKEND_MODULAR_MONOLITH.md) (schema per module) and
[ADR 0010](adr/0010_API_DESIGN.md) (UUIDv7, money). Field sources: `docs/integrations/STORYOUS.md`.

## Conventions

- PostgreSQL 18. One schema per module, plus `platform` for infrastructure tables.
- snake_case names. Primary keys `id uuid primary key default uuidv7()`.
- Every table has `created_at timestamptz not null default now()`; mutable tables also
  `updated_at timestamptz not null`. Editable-by-user tables have `version int not null default 0`
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
- Flyway: one history table in `platform`; migrations named `V001__account_init.sql`, …; each
  migration schema-qualifies every object.

## `platform` (infrastructure, owned by `common`)

| Table | Purpose / key columns |
|-------|------------------------|
| `event_publication` | Spring Modulith event publication registry (DDL from Spring Modulith 2.x JDBC). Set `spring.modulith.events.jdbc.schema=platform` and disable its schema initialization (Flyway owns the DDL). A job purges completed publications older than 7 days. |
| `scheduled_tasks` | db-scheduler task table (DDL from db-scheduler). |
| `idempotency_record` | `user_id`, `key`, `method`, `path`, `request_hash`, `status (IN_PROGRESS\|COMPLETED)`, `response_status`, `response_body jsonb`, `created_at`, `expires_at` (24 h). PK `(user_id, key)`. |

## `account`

| Table | Key columns | Notes |
|-------|-------------|-------|
| `organization` | `id`, `name`, `version` | The customer business. |
| `app_user` | `id`, `email citext unique`, `email_verified_at`, `display_name`, `locale` | |
| `user_identity` | `id`, `user_id → app_user`, `provider (APPLE\|GOOGLE\|EMAIL)`, `subject`, `email` | Unique `(provider, subject)`. Apple private-relay emails stored as given. |
| `email_login_code` | `id`, `email`, `code_hash`, `expires_at`, `attempts`, `consumed_at`, `requested_ip` | 10 min, max 5 attempts (ADR 0004). Index `(email, created_at desc)`. Purged after 1 day. |
| `refresh_token` | `id`, `user_id`, `family_id`, `token_hash unique`, `expires_at`, `used_at`, `revoked_at`, `replaced_by_id`, `device_name` | Rotation + reuse detection by family. |
| `membership` | `id`, `organization_id`, `user_id`, `role (OWNER\|STAFF)` | Unique `(organization_id, user_id)`. First release: OWNER only. |
| `venue` | `id`, `organization_id`, `name`, `address_line`, `city`, `time_zone` (default `Europe/Prague`), `currency char(3)`, `business_day_start time` (default `05:00`), `status (ACTIVE\|DISABLED)`, `version` | Created when the POS sync discovers a place. |
| `followed_venue` | `user_id`, `venue_id` | PK `(user_id, venue_id)`. Venues shown in Today/Sales (A5). |

Account deletion (`DELETE /me`, App Store 5.1.1(v), GDPR art. 17):

- The `app_user` and its personal data are deleted **immediately** (cascades identities, tokens,
  memberships, followed venues). Deactivation alone is not enough.
- If the user was the **last owner** of an organization, the organization is marked for deletion
  and all its data (venues, POS read model incl. staff names, reporting, stock) is deleted by a
  background job after **30 days** (inside GDPR's one-month window). During those 30 days an
  accidental deletion can be reversed through support. Legal wording to be confirmed.

## `pos` (read model of the POS, written only by the sync)

| Table | Key columns | Notes |
|-------|-------------|-------|
| `pos_connection` | `id`, `organization_id`, `provider (STORYOUS)`, `external_merchant_id`, `status (PENDING\|ACTIVE\|ERROR\|REVOKED)`, `last_error`, `last_synced_at` | Storyous uses **our app's** client credentials (server config), so no per-merchant secret is stored. Unique `(provider, external_merchant_id)`. |
| `venue_link` | `venue_id` (PK), `connection_id`, `external_place_id` | Unique `(connection_id, external_place_id)`. |
| `sync_cursor` | `venue_id`, `resource (BILLS\|MENU\|DESKS)`, `cursor text`, `last_success_at`, `last_error` | PK `(venue_id, resource)`. Bills cursor = last `_lastModifiedAt`. |
| `sync_job` | `id`, `connection_id`, `kind (INITIAL\|INCREMENTAL\|BACKFILL)`, `status`, `started_at`, `finished_at`, `steps jsonb` | Steps = `[{name, state, progress, countLabel}]` for A4. |
| `category` | `id`, `venue_id`, `external_id`, `name`, `parent_id` | Unique `(venue_id, external_id)`. Storyous menus are merchant-level; we keep a copy per venue for simple queries. |
| `product` | `id`, `venue_id`, `external_id`, `category_id`, `name`, `measure`, `ean`, `vat_rate`, `price_minor`, `active` | Unique `(venue_id, external_id)`. |
| `section` | `id`, `venue_id`, `external_id`, `name`, `color`, `removed` | Storyous desk sections = zones. |
| `desk` | `id`, `venue_id`, `external_id`, `section_id`, `name`, `code`, `is_virtual`, `removed` | No seats in Storyous (see `reporting.desk_capacity`). |
| `staff_member` | `id`, `connection_id`, `external_id` (personId), `full_name`, `user_name` | Unique `(connection_id, external_id)`. Merchant-level (a person can work in several venues). Filled from bill `createdBy`/`paidBy`. |
| `bill` | `id`, `venue_id`, `external_id` (billId), `session_started_at`, `pos_created_at`, `paid_at`, `fiscalized_at`, `business_day date`, `total_minor`, `total_without_tax_minor`, `discount_minor`, `rounding_minor`, `tips_minor`, `currency`, `refunded`, `deleted`, `desk_id`, `created_by_staff_id`, `paid_by_staff_id`, `guest_count`, `external_modified_at` | Unique `(venue_id, external_id)`. Indexes `(venue_id, business_day)`, `(venue_id, paid_at)`, `(venue_id, external_modified_at)`. `business_day` computed from `paid_at`, venue time zone and cut-off (METRICS, M1). |
| `bill_item` | `id`, `bill_id → bill (cascade)`, `venue_id`, `line_no`, `product_id`, `external_product_id`, `name`, `quantity`, `measure`, `unit_price_minor`, `vat_rate`, `discount_minor`, `total_minor` | Storyous items have no stable ID: when a bill changes, its items are **replaced** in the same transaction. Skip entirely if `external_modified_at` is unchanged. Index `(venue_id, product_id)`. |
| `bill_item_discount` | `id`, `bill_item_id (cascade)`, `discount_type`, `amount_type`, `percent_value`, `absolute_minor`, `original_price_minor`, `note` | |
| `payment` | `id`, `bill_id (cascade)`, `method_raw`, `method_kind (CARD\|CASH\|OTHER)`, `amount_minor` | Mapping raw → kind per provider. |
| `raw_payload` | `id`, `venue_id`, `resource`, `external_id`, `payload jsonb`, `fetched_at` | Debug/reprocessing, kept 30 days. |

POS capabilities (guest count, open bills, item voids, desks, attendance) are **constants per
provider in code**, not a table; the API exposes them per venue.

**To verify with real data:** units of Storyous item `price` (the docs example mixes integer item
prices with a decimal-string bill total) and the meaning of `sessionCreated` vs `createdAt`.

## `reporting` (derived; rebuildable from `pos`)

Own rollup tables, not materialized views: PostgreSQL materialized views recompute everything on
refresh, while these tables are refreshed per `(venue_id, business_day)` after each `BillsSynced`
event. A rebuild job recomputes any range from `pos`. Hours are local clock hours; on the DST
fall-back night the repeated hour is merged into one row.

| Table | Grain (PK) | Columns | Basis |
|-------|-----------|---------|-------|
| `hourly_sales` | `venue_id, business_day, hour` | `revenue_minor`, `revenue_without_tax_minor`, `bills`, `guests`, `tips_minor`, `discount_minor`, `card_minor`, `cash_minor`, `other_minor`, `refunded_minor`, `deleted_bills` | Local hour of `paid_at` |
| `hourly_activity` | `venue_id, business_day, hour` | `sessions`, `guests` | Local hour of `session_started_at` (hourly patterns, 02b) |
| `product_hourly_sales` | `venue_id, business_day, hour, product_id` | `quantity`, `gross_minor`, `discount_minor` | Session hour (no per-item order time) |
| `staff_daily_sales` | `venue_id, business_day, staff_id` | `bills`, `revenue_minor`, `guests`, `discount_minor`, `deleted_bills` (by `created_by`); `tips_minor` (by `paid_by`) | Sales to the person who **opened** the bill, tips to the person who **closed** it (answers M6). Both IDs stay on `pos.bill`, so a per-venue setting can be added later. |
| `desk_daily_sales` | `venue_id, business_day, desk_id` | `bills`, `revenue_minor`, `guests`, `occupied_minutes` | `occupied_minutes` = Σ(paid_at − session_started_at) |
| `discount_daily` | `venue_id, business_day, discount_type` | `records`, `amount_minor` | |
| `desk_capacity` | `venue_id, desk_id` | `estimated`, `override`, `updated_at`, `version` | Estimated = highest guest count seen in 8 weeks; owner can override (labelled "estimated") |
| `rollup_state` | `venue_id, business_day` | `refreshed_at`, `source_max_modified_at` | Tracks what is up to date |

Sizing: a busy bar has ~300 bills/day → ~100k bills/year per venue. No partitioning for now;
revisit at millions of rows (range partitioning by `business_day`).

## `stock`

Pending P13/P14. Model in `docs/domain/MODEL.md` and `docs/domain/STOCK.md` (append-only
movements, receipts, counts, transfers, recipes, locations).
