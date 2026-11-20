-- reporting module: rollups derived from pos, rebuildable at any time
-- (docs/architecture/DB_SCHEMA.md#reporting). Refreshed per (venue_id, business_day) after BillsSynced.
-- hour = local clock hour 0-23; the repeated DST fall-back hour is merged into one row.
-- product_id, staff_id and desk_id point to pos rows without foreign keys (cross-module rule).
-- Amounts are in the venue's currency.

create schema reporting;

create table reporting.hourly_sales (
    venue_id                  uuid        not null references account.venue (id),
    business_day              date        not null,
    hour                      smallint    not null check (hour between 0 and 23),
    revenue_minor             bigint      not null default 0,
    revenue_without_tax_minor bigint      not null default 0,
    bills                     int         not null default 0,
    guests                    int         not null default 0,
    tips_minor                bigint      not null default 0,
    discount_minor            bigint      not null default 0,
    card_minor                bigint      not null default 0,
    cash_minor                bigint      not null default 0,
    other_minor               bigint      not null default 0,
    refunded_minor            bigint      not null default 0,
    deleted_bills             int         not null default 0,
    created_at                timestamptz not null default now(),
    primary key (venue_id, business_day, hour)
);

create table reporting.hourly_activity (
    venue_id     uuid        not null references account.venue (id),
    business_day date        not null,
    hour         smallint    not null check (hour between 0 and 23),
    sessions     int         not null default 0,
    guests       int         not null default 0,
    created_at   timestamptz not null default now(),
    primary key (venue_id, business_day, hour)
);

create table reporting.product_hourly_sales (
    venue_id       uuid           not null references account.venue (id),
    business_day   date           not null,
    hour           smallint       not null check (hour between 0 and 23),
    product_id     uuid           not null,
    quantity       numeric(12, 3) not null default 0,
    gross_minor    bigint         not null default 0,
    discount_minor bigint         not null default 0,
    created_at     timestamptz    not null default now(),
    primary key (venue_id, business_day, hour, product_id)
);

-- Sales, guests, discounts and deleted bills by the staff member who opened the bill; tips by the
-- one who closed it (METRICS M6).
create table reporting.staff_daily_sales (
    venue_id       uuid        not null references account.venue (id),
    business_day   date        not null,
    staff_id       uuid        not null,
    bills          int         not null default 0,
    revenue_minor  bigint      not null default 0,
    guests         int         not null default 0,
    discount_minor bigint      not null default 0,
    deleted_bills  int         not null default 0,
    tips_minor     bigint      not null default 0,
    created_at     timestamptz not null default now(),
    primary key (venue_id, business_day, staff_id)
);

create table reporting.desk_daily_sales (
    venue_id         uuid        not null references account.venue (id),
    business_day     date        not null,
    desk_id          uuid        not null,
    bills            int         not null default 0,
    revenue_minor    bigint      not null default 0,
    guests           int         not null default 0,
    occupied_minutes int         not null default 0,
    created_at       timestamptz not null default now(),
    primary key (venue_id, business_day, desk_id)
);

create table reporting.discount_daily (
    venue_id      uuid        not null references account.venue (id),
    business_day  date        not null,
    discount_type text        not null,
    records       int         not null default 0,
    amount_minor  bigint      not null default 0,
    created_at    timestamptz not null default now(),
    primary key (venue_id, business_day, discount_type)
);

-- estimated = highest guest count seen in 8 weeks; override is set by the owner.
create table reporting.desk_capacity (
    venue_id   uuid        not null references account.venue (id),
    desk_id    uuid        not null,
    estimated  int,
    override   int,
    version    int         not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (venue_id, desk_id)
);

create table reporting.rollup_state (
    venue_id               uuid        not null references account.venue (id),
    business_day           date        not null,
    refreshed_at           timestamptz not null,
    source_max_modified_at timestamptz,
    created_at             timestamptz not null default now(),
    primary key (venue_id, business_day)
);
