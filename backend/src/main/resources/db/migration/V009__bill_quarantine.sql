-- Bills the sync could not read (no detail, or a value it can't store exactly): kept by external ID so
-- later runs retry them and dashboards can say "N bills couldn't be read" (rule 2; STORYOUS.md, sync
-- strategy). A bill that reads leaves the table.
create table pos.bill_quarantine (
    venue_id        uuid        not null references account.venue (id),
    external_id     text        not null,
    reason          text        not null,
    first_seen_at   timestamptz not null,
    last_attempt_at timestamptz not null,
    attempts        int         not null default 1,
    primary key (venue_id, external_id)
);
-- The retry query: a venue's rows by last attempt (the PK orders by external_id, not by time).
create index bill_quarantine_venue_id_last_attempt_at_idx on pos.bill_quarantine (venue_id, last_attempt_at);

-- The POS desk ID of a bill, so desk_id can be filled once the desk is synced (DESK has no row yet when
-- a bill arrives first). Null when the POS sends none.
alter table pos.bill add column external_desk_id text;

-- A bill whose header (totals, flags, payments) reads but whose detail or items don't: stored without
-- lines and flagged, so its revenue, refund or deletion still counts; the detail stays quarantined.
alter table pos.bill add column items_unreadable boolean not null default false;
