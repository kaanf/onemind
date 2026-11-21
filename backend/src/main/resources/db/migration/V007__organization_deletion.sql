-- Account deletion (DB_SCHEMA.md): an organization whose last owner deleted their account is deleted
-- with all its data after this time, by a job added with the organization data (Phase 4).
alter table account.organization add column deletion_due_at timestamptz;
create index organization_deletion_due_at_idx on account.organization (deletion_due_at) where deletion_due_at is not null;
