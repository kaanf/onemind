-- Refunded bills of the hour (a count; refunded_minor holds their amount): the "Refunds · n" records of
-- Discounts & losses (02c, METRICS losses). Days stored before this column read 0 until the rebuild job
-- recomputes them (RollupVersion 3); readers hide version-dependent figures until then (rule 2).
alter table reporting.hourly_sales add column refunded_bills int not null default 0;
