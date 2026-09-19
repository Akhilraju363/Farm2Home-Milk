-- Adds a district column alongside the existing free-text city/state on customer_addresses.
-- Nullable and additive only: existing rows (and every other service that reads city/state as
-- plain strings) are unaffected. New addresses collected via the redesigned registration Address
-- step populate this from the district dropdown, which is backed by location_districts.
ALTER TABLE customer.customer_addresses ADD COLUMN district VARCHAR(100);
