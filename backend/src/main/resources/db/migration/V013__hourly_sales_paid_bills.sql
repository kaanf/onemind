-- Bills of the hour that count in paidRevenue (not deleted, not refunded, payments add up): the divisor
-- of averageBill (METRICS, revenue). `bills` also holds refunded and payment-mismatch bills.
alter table reporting.hourly_sales add column paid_bills int not null default 0;

-- refunded_minor split by payment kind (card + cash + other = refunded_minor), so a reader can take
-- refunded bills off the card/cash/other split as it takes them off revenue (METRICS, revenue; M13).
alter table reporting.hourly_sales add column refunded_card_minor bigint not null default 0;
alter table reporting.hourly_sales add column refunded_cash_minor bigint not null default 0;
alter table reporting.hourly_sales add column refunded_other_minor bigint not null default 0;

-- Days stored before these columns read 0 here until the rebuild job recomputes them (RollupVersion 2);
-- readers leave days below that version out of averageBill and the payment split instead (rule 2).
