-- Completes Andhra Pradesh's city/town master data. V8 completed the 26-district list itself but
-- left the 13 newly formed districts with zero location_cities rows (the 13 pre-2022 districts
-- each already had exactly one city - their own headquarters town, per V5's original seeding
-- convention). This migration is purely additive: INSERT ... SELECT against the existing district
-- rows (never a hardcoded district UUID), guarded by the existing (district_id, name) unique
-- constraint, so it is safe to run more than once and never touches an existing row.
--
-- Scope: one city/town per new district (its own official headquarters town), matching V5's
-- established "district's own name is its seed city" convention - EXCEPT Annamayya, which gets
-- the fuller set the business explicitly needs (its own headquarters plus every other
-- addressable municipality/major town in the district, including the town Farm2Home's own
-- configured business address sits in - Pileru).
--
-- Sources (fetched 2026-09-26 via WebSearch/WebFetch - see docs/LOCATION_MASTER_DATA.md for the
-- full citation list): each headquarters town below is confirmed by that district's own official
-- AP government portal ("About District" / "District Profile" page) and cross-checked against
-- Wikipedia's administrative-divisions summary. Annamayya's four municipalities plus B Kothakota
-- nagar panchayat come directly from https://annamayya.ap.gov.in/public-utility-category/municipality/
-- (paginated, both pages fetched); Pileru and Rajampet are documented on that same portal as its
-- Pileru and Rajampet revenue-division headquarters (https://en.wikipedia.org/wiki/Annamayya_district)
-- - real, government-documented towns, not municipalities by that portal's own category, but
-- genuine addressable towns, not an invented or village-level entry, and the district explicitly
-- named per this task's own requirement.

-- One headquarters-town city for each of the 12 new (non-Annamayya) districts that V8 added with
-- no city data yet. INSERT ... SELECT against the district's own existing row - never a literal
-- UUID - and ON CONFLICT protects against a re-run or a row that already exists for any reason.
INSERT INTO customer.location_cities (district_id, name)
SELECT d.id, v.city_name
FROM customer.location_districts d
JOIN (VALUES
  ('Alluri Sitharama Raju',       'Paderu'),
  ('Anakapalli',                  'Anakapalli'),
  ('Bapatla',                     'Bapatla'),
  ('Dr. B.R. Ambedkar Konaseema', 'Amalapuram'),
  ('Eluru',                       'Eluru'),
  ('Kakinada',                    'Kakinada'),
  ('Nandyal',                     'Nandyal'),
  ('NTR',                         'Vijayawada'),
  ('Palnadu',                     'Narasaraopet'),
  ('Parvathipuram Manyam',        'Parvathipuram'),
  ('Sri Sathya Sai',              'Puttaparthi'),
  ('Tirupati',                    'Tirupati')
) AS v(district_name, city_name) ON v.district_name = d.name
WHERE d.state_id = (SELECT id FROM customer.location_states WHERE code = 'AP')
ON CONFLICT (district_id, name) DO NOTHING;

-- Annamayya: its own headquarters (Madanapalle) plus every other municipality/major addressable
-- town the district's official portal documents, including Pileru itself - the town Farm2Home's
-- own configured business address (ST Colony, Yerraguntlapalle, Pileru, Chittoor [district name
-- on existing free-text customer/business address records pre-dates this master data - see
-- docs/LOCATION_MASTER_DATA.md], Andhra Pradesh - 517214) sits in.
INSERT INTO customer.location_cities (district_id, name)
SELECT d.id, v.city_name
FROM customer.location_districts d
JOIN (VALUES
  ('Madanapalle'),
  ('Rayachoti'),
  ('Rajampet'),
  ('Pileru'),
  ('Punganur'),
  ('B Kothakota')
) AS v(city_name) ON true
WHERE d.state_id = (SELECT id FROM customer.location_states WHERE code = 'AP')
  AND d.name = 'Annamayya'
ON CONFLICT (district_id, name) DO NOTHING;

-- Deliberately NOT done here (see docs/LOCATION_MASTER_DATA.md "Known gaps"):
--   - No pincode column added to location_cities - this project's existing architecture keeps
--     pincode on the customer's own address record (free text), never on the city master. Adding
--     one here would be an unrelated schema change this migration has no need for.
--   - No bulk rewrite of any existing customer_addresses row. Those columns are free text, not a
--     foreign key to this table, and nothing here can deterministically prove which existing
--     free-text "Chittoor"-district address should now read "Annamayya" - left untouched.
--   - No additional villages/mandals seeded beyond each district's own headquarters (or, for
--     Annamayya, its documented municipalities/major towns) - this master data is for customer
--     address selection, not an exhaustive administrative gazetteer.
