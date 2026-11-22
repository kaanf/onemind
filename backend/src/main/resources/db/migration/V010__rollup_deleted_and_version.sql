-- possibleLoss needs the amount of whole deleted bills, not only their count (METRICS, losses).
alter table reporting.hourly_sales add column deleted_minor bigint not null default 0;

-- Which rollup calculation produced a day's rows: the rebuild job recomputes days below the version in
-- code (reporting/domain/RollupVersion), so a METRICS change reaches past days.
alter table reporting.rollup_state add column rollup_version int not null default 0;
