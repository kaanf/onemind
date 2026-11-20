-- pos module: read model of the POS, written only by the sync (docs/architecture/DB_SCHEMA.md#pos).
-- Raw POS values (payment method, discount type, measure) are plain text without CHECK, so a new
-- POS value never breaks the sync.

create schema pos;

-- organization_id has no foreign key: cross-module keys only go to account.venue and account.app_user.
create table pos.pos_connection (
    id                   uuid primary key     default uuidv7(),
    organization_id      uuid        not null,
    provider             text        not null check (provider in ('STORYOUS')),
    external_merchant_id text        not null,
    status               text        not null default 'PENDING'
        check (status in ('PENDING', 'ACTIVE', 'ERROR', 'REVOKED')),
    last_error           text,
    last_synced_at       timestamptz,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    unique (provider, external_merchant_id)
);
create index pos_connection_organization_id_idx on pos.pos_connection (organization_id);

create table pos.venue_link (
    venue_id          uuid primary key references account.venue (id),
    connection_id     uuid        not null references pos.pos_connection (id),
    external_place_id text        not null,
    created_at        timestamptz not null default now(),
    unique (connection_id, external_place_id)
);

create table pos.sync_cursor (
    venue_id        uuid        not null references account.venue (id),
    resource        text        not null check (resource in ('BILLS', 'MENU', 'DESKS')),
    cursor          text,
    last_success_at timestamptz,
    last_error      text,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    primary key (venue_id, resource)
);

create table pos.sync_job (
    id            uuid primary key     default uuidv7(),
    connection_id uuid        not null references pos.pos_connection (id),
    kind          text        not null check (kind in ('INITIAL', 'INCREMENTAL', 'BACKFILL')),
    status        text        not null default 'QUEUED'
        check (status in ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    started_at    timestamptz,
    finished_at   timestamptz,
    steps         jsonb       not null default '[]',
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);
create index sync_job_connection_id_created_at_idx on pos.sync_job (connection_id, created_at desc);

create table pos.category (
    id          uuid primary key     default uuidv7(),
    venue_id    uuid        not null references account.venue (id),
    external_id text        not null,
    name        text        not null,
    parent_id   uuid references pos.category (id) on delete set null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    unique (venue_id, external_id)
);

create table pos.product (
    id          uuid primary key     default uuidv7(),
    venue_id    uuid        not null references account.venue (id),
    external_id text        not null,
    category_id uuid references pos.category (id) on delete set null,
    name        text        not null,
    measure     text,
    ean         text,
    vat_rate    numeric(6, 4),
    price_minor bigint,
    active      boolean     not null default true,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    unique (venue_id, external_id)
);

create table pos.section (
    id          uuid primary key     default uuidv7(),
    venue_id    uuid        not null references account.venue (id),
    external_id text        not null,
    name        text        not null,
    color       text,
    removed     boolean     not null default false,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    unique (venue_id, external_id)
);

create table pos.desk (
    id          uuid primary key     default uuidv7(),
    venue_id    uuid        not null references account.venue (id),
    external_id text        not null,
    section_id  uuid references pos.section (id) on delete set null,
    name        text        not null,
    code        text,
    is_virtual  boolean     not null default false,
    removed     boolean     not null default false,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    unique (venue_id, external_id)
);

-- Merchant-level: one person can work in several venues of the same connection.
create table pos.staff_member (
    id            uuid primary key     default uuidv7(),
    connection_id uuid        not null references pos.pos_connection (id),
    external_id   text        not null,
    full_name     text,
    user_name     text,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    unique (connection_id, external_id)
);

-- business_day is computed from paid_at, the venue time zone and business_day_start (METRICS M1).
create table pos.bill (
    id                      uuid primary key     default uuidv7(),
    venue_id                uuid        not null references account.venue (id),
    external_id             text        not null,
    session_started_at      timestamptz,
    pos_created_at          timestamptz,
    paid_at                 timestamptz not null,
    fiscalized_at           timestamptz,
    business_day            date        not null,
    total_minor             bigint      not null,
    total_without_tax_minor bigint,
    discount_minor          bigint      not null default 0,
    rounding_minor          bigint      not null default 0,
    tips_minor              bigint      not null default 0,
    currency                char(3)     not null,
    refunded                boolean     not null default false,
    deleted                 boolean     not null default false,
    desk_id                 uuid references pos.desk (id) on delete set null,
    created_by_staff_id     uuid references pos.staff_member (id) on delete set null,
    paid_by_staff_id        uuid references pos.staff_member (id) on delete set null,
    guest_count             int,
    external_modified_at    timestamptz not null,
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now(),
    unique (venue_id, external_id)
);
create index bill_venue_id_business_day_idx on pos.bill (venue_id, business_day);
create index bill_venue_id_paid_at_idx on pos.bill (venue_id, paid_at);
create index bill_venue_id_external_modified_at_idx on pos.bill (venue_id, external_modified_at);

-- Storyous items have no stable ID: when a bill changes, its items are replaced in one transaction.
create table pos.bill_item (
    id                  uuid primary key     default uuidv7(),
    bill_id             uuid        not null references pos.bill (id) on delete cascade,
    venue_id            uuid        not null references account.venue (id),
    line_no             int         not null,
    product_id          uuid references pos.product (id) on delete set null,
    external_product_id text,
    name                text        not null,
    quantity            numeric(12, 3) not null,
    measure             text,
    unit_price_minor    bigint,
    vat_rate            numeric(6, 4),
    discount_minor      bigint      not null default 0,
    total_minor         bigint      not null,
    created_at          timestamptz not null default now(),
    unique (bill_id, line_no)
);
create index bill_item_venue_id_product_id_idx on pos.bill_item (venue_id, product_id);

create table pos.bill_item_discount (
    id                   uuid primary key     default uuidv7(),
    bill_item_id         uuid        not null references pos.bill_item (id) on delete cascade,
    discount_type        text,
    amount_type          text,
    percent_value        numeric(7, 4),
    absolute_minor       bigint,
    original_price_minor bigint,
    note                 text,
    created_at           timestamptz not null default now()
);
create index bill_item_discount_bill_item_id_idx on pos.bill_item_discount (bill_item_id);

create table pos.payment (
    id           uuid primary key     default uuidv7(),
    bill_id      uuid        not null references pos.bill (id) on delete cascade,
    method_raw   text        not null,
    method_kind  text        not null check (method_kind in ('CARD', 'CASH', 'OTHER')),
    amount_minor bigint      not null,
    created_at   timestamptz not null default now()
);
create index payment_bill_id_idx on pos.payment (bill_id);

-- Debug/reprocessing copies of POS responses, kept 30 days.
create table pos.raw_payload (
    id          uuid primary key     default uuidv7(),
    venue_id    uuid        not null references account.venue (id),
    resource    text        not null,
    external_id text        not null,
    payload     jsonb       not null,
    fetched_at  timestamptz not null default now(),
    created_at  timestamptz not null default now()
);
create index raw_payload_venue_id_resource_external_id_idx on pos.raw_payload (venue_id, resource, external_id);
create index raw_payload_fetched_at_idx on pos.raw_payload (fetched_at);
