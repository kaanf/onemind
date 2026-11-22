-- possibleLoss needs the amount of whole deleted bills, not only their count (METRICS, losses).
alter table reporting.hourly_sales add column deleted_minor bigint not null default 0;

-- Which rollup calculation produced a day's rows: the rebuild job recomputes days below the version in
-- code (reporting/domain/RollupVersion), so a METRICS change reaches past days.
alter table reporting.rollup_state add column rollup_version int not null default 0;

-- Bills of the hour without a net (without tax) total from the POS: revenue_without_tax_minor sums only
-- the others, so a reader must not show the net figure while this is above 0 (rule 2; M2 stays open).
alter table reporting.hourly_sales add column bills_missing_net int not null default 0;
