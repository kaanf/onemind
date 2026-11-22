-- Interim demo onboarding (dev/demo only, product rule 2): a connection to the demo POS is marked
-- MOCK, so it can never be taken for a Storyous one. Only widens the allowed values.
alter table pos.pos_connection drop constraint pos_connection_provider_check;
alter table pos.pos_connection add constraint pos_connection_provider_check check (provider in ('STORYOUS', 'MOCK'));
