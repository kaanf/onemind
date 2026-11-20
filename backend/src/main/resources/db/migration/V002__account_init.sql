-- account module: users, auth, organizations, venues, memberships (docs/architecture/DB_SCHEMA.md#account).

-- Installed in public so its operators resolve for every connection, whatever its search_path.
create extension if not exists citext with schema public;

create schema account;

create table account.organization (
    id         uuid primary key     default uuidv7(),
    name       text        not null,
    version    int         not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table account.app_user (
    id                uuid primary key     default uuidv7(),
    email             public.citext not null unique,
    email_verified_at timestamptz,
    display_name      text,
    locale            text,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

create table account.user_identity (
    id         uuid primary key     default uuidv7(),
    user_id    uuid        not null references account.app_user (id) on delete cascade,
    provider   text        not null check (provider in ('APPLE', 'GOOGLE', 'EMAIL')),
    subject    text        not null,
    email      text,
    created_at timestamptz not null default now(),
    unique (provider, subject)
);
create index user_identity_user_id_idx on account.user_identity (user_id);

-- Not linked to app_user: a code can be requested before the user exists. Purged after 1 day.
create table account.email_login_code (
    id           uuid primary key     default uuidv7(),
    email        public.citext not null,
    code_hash    text        not null,
    expires_at   timestamptz not null,
    attempts     int         not null default 0,
    consumed_at  timestamptz,
    requested_ip inet,
    created_at   timestamptz not null default now()
);
create index email_login_code_email_created_at_idx on account.email_login_code (email, created_at desc);

create table account.refresh_token (
    id             uuid primary key     default uuidv7(),
    user_id        uuid        not null references account.app_user (id) on delete cascade,
    family_id      uuid        not null,
    token_hash     text        not null unique,
    expires_at     timestamptz not null,
    used_at        timestamptz,
    revoked_at     timestamptz,
    replaced_by_id uuid references account.refresh_token (id) on delete set null,
    device_name    text,
    created_at     timestamptz not null default now()
);
create index refresh_token_user_id_idx on account.refresh_token (user_id);
create index refresh_token_family_id_idx on account.refresh_token (family_id);

create table account.membership (
    id              uuid primary key     default uuidv7(),
    organization_id uuid        not null references account.organization (id) on delete cascade,
    user_id         uuid        not null references account.app_user (id) on delete cascade,
    role            text        not null check (role in ('OWNER', 'STAFF')),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    unique (organization_id, user_id)
);
create index membership_user_id_idx on account.membership (user_id);

-- Venue data in other modules references this table; the organization deletion job removes it
-- module by module, so there is no cascade from organization.
create table account.venue (
    id                 uuid primary key     default uuidv7(),
    organization_id    uuid        not null references account.organization (id),
    name               text        not null,
    address_line       text,
    city               text,
    time_zone          text        not null default 'Europe/Prague',
    currency           char(3)     not null,
    business_day_start time        not null default '05:00',
    status             text        not null default 'ACTIVE' check (status in ('ACTIVE', 'DISABLED')),
    version            int         not null default 0,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now()
);
create index venue_organization_id_idx on account.venue (organization_id);

create table account.followed_venue (
    user_id    uuid        not null references account.app_user (id) on delete cascade,
    venue_id   uuid        not null references account.venue (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, venue_id)
);
create index followed_venue_venue_id_idx on account.followed_venue (venue_id);
