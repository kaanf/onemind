-- A place the POS reports as disabled/hidden, or stops reporting, deactivates its link: the sync stops
-- reading it, but the account.venue and its history stay readable (pending owner confirmation, see
-- docs/integrations/STORYOUS.md, sync strategy). Re-reported as active → active again.
alter table pos.venue_link add column active boolean not null default true;
