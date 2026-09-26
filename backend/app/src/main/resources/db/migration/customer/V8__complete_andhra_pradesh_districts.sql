-- Completes Andhra Pradesh's district list to the current (post-2022 reorganization) 26
-- districts. V5 seeded only the pre-2022 13-district list. This migration is additive/corrective
-- only - it does not touch any other state, does not delete or recreate any existing district row
-- (customer_addresses.district/city/state are plain free-text columns, not foreign keys to this
-- table, but location_cities.district_id IS a real FK - see the 13 existing AP districts' city
-- rows below, all left untouched), and is safe to run more than once.
--
-- What happened to each pre-2022 district (for anyone reading this later):
--   Anantapur, Chittoor, East Godavari, Guntur, Krishna, Kurnool, Prakasam, Srikakulam,
--   Visakhapatnam, Vizianagaram, West Godavari, YSR Kadapa - all kept their existing name (each
--   became geographically smaller as a new district was carved out alongside it; the row/id/name
--   already in location_districts stays correct and is not touched here).
--   Nellore - officially renamed "Sri Potti Sriramulu Nellore" (same district, not split) -
--   corrected below via UPDATE, preserving the row's id so nothing referencing it breaks.
-- The 13 newly formed districts (Alluri Sitharama Raju, Anakapalli, Annamayya, Bapatla,
-- Dr. B.R. Ambedkar Konaseema, Eluru, Kakinada, Nandyal, NTR, Palnadu, Parvathipuram Manyam,
-- Sri Sathya Sai, Tirupati) are inserted new, continuing the AP-xx code sequence V5 started.

-- Correct the one outdated name among the existing 13 - UPDATE, not delete+insert, so the row's
-- id (and anything that may come to reference it later) is preserved. Guarded by the old name so
-- this is a no-op if already applied.
UPDATE customer.location_districts
SET name = 'Sri Potti Sriramulu Nellore'
WHERE state_id = (SELECT id FROM customer.location_states WHERE code = 'AP')
  AND name = 'Nellore';

-- The 13 districts formed by the 2022 reorganization that V5 never had. ON CONFLICT on the same
-- (state_id, name) uniqueness V5 already relies on - safe if partially or fully re-run.
INSERT INTO customer.location_districts (state_id, name, code) VALUES
  ((SELECT id FROM customer.location_states WHERE code = 'AP'), 'Alluri Sitharama Raju', 'AP-14'),
  ((SELECT id FROM customer.location_states WHERE code = 'AP'), 'Anakapalli', 'AP-15'),
  ((SELECT id FROM customer.location_states WHERE code = 'AP'), 'Annamayya', 'AP-16'),
  ((SELECT id FROM customer.location_states WHERE code = 'AP'), 'Bapatla', 'AP-17'),
  ((SELECT id FROM customer.location_states WHERE code = 'AP'), 'Dr. B.R. Ambedkar Konaseema', 'AP-18'),
  ((SELECT id FROM customer.location_states WHERE code = 'AP'), 'Eluru', 'AP-19'),
  ((SELECT id FROM customer.location_states WHERE code = 'AP'), 'Kakinada', 'AP-20'),
  ((SELECT id FROM customer.location_states WHERE code = 'AP'), 'Nandyal', 'AP-21'),
  ((SELECT id FROM customer.location_states WHERE code = 'AP'), 'NTR', 'AP-22'),
  ((SELECT id FROM customer.location_states WHERE code = 'AP'), 'Palnadu', 'AP-23'),
  ((SELECT id FROM customer.location_states WHERE code = 'AP'), 'Parvathipuram Manyam', 'AP-24'),
  ((SELECT id FROM customer.location_states WHERE code = 'AP'), 'Sri Sathya Sai', 'AP-25'),
  ((SELECT id FROM customer.location_states WHERE code = 'AP'), 'Tirupati', 'AP-26')
ON CONFLICT (state_id, name) DO NOTHING;

-- Known, deliberate gap (see the audit report): none of these 13 new districts get a
-- location_cities row here - no source city dataset for them was available in this project, and
-- inventing one was explicitly out of scope. The City dropdown will show "No cities available"
-- for these until real city data is added in a future migration.
