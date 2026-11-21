-- platform.idempotency_record: requests sent with an Idempotency-Key and their responses, kept 24 h
-- (docs/architecture/DB_SCHEMA.md#platform, docs/api/README.md#updates-concurrency-idempotency).
create table platform.idempotency_record (
    user_id          uuid        not null references account.app_user (id) on delete cascade,
    key              uuid        not null,
    method           text        not null,
    path             text        not null,
    -- SHA-256 of method, path, query and body: a retry must send the same request.
    request_hash     bytea       not null,
    -- New for every claim (first request or takeover): only that request may complete or release it.
    claim_id         uuid        not null,
    status           text        not null check (status in ('IN_PROGRESS', 'COMPLETED')),
    response_status  int,
    -- Replayed headers only (Content-Type, Location), as {"name": "value"}.
    response_headers jsonb,
    -- text, not jsonb: a replay must send the original bytes.
    response_body    text,
    created_at       timestamptz not null,
    expires_at       timestamptz not null,
    primary key (user_id, key),
    check (status = 'IN_PROGRESS' or response_status is not null)
);

-- The purge job deletes expired records.
create index idempotency_record_expires_at_idx on platform.idempotency_record (expires_at);
