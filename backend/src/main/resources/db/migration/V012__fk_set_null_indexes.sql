-- Indexes on the "on delete set null" foreign keys (DB_SCHEMA.md: every foreign key is indexed):
-- without them, deleting a product, desk, staff member, category, section or rotated refresh token
-- scans the referencing table. Partial: rows without a reference never need the lookup.
create index refresh_token_replaced_by_id_idx on account.refresh_token (replaced_by_id) where replaced_by_id is not null;
create index category_parent_id_idx on pos.category (parent_id) where parent_id is not null;
create index product_category_id_idx on pos.product (category_id) where category_id is not null;
create index desk_section_id_idx on pos.desk (section_id) where section_id is not null;
create index bill_desk_id_idx on pos.bill (desk_id) where desk_id is not null;
create index bill_created_by_staff_id_idx on pos.bill (created_by_staff_id) where created_by_staff_id is not null;
create index bill_paid_by_staff_id_idx on pos.bill (paid_by_staff_id) where paid_by_staff_id is not null;
create index bill_item_product_id_idx on pos.bill_item (product_id) where product_id is not null;
