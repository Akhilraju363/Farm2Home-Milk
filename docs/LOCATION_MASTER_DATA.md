# Location Master Data — India / Andhra Pradesh

This documents the India State → District → City/Town reference data (`customer.location_states`,
`customer.location_districts`, `customer.location_cities`, all in customer-service) that backs
every State/District/City cascading dropdown in the app (registration, add/edit delivery address,
Location Master). It is not a description of code architecture — see `LocationController`/
`LocationMasterController`/`LocationServiceImpl`/`LocationMasterServiceImpl` for that.

## Hierarchy

```
India
 └── State           (location_states)
      └── District    (location_districts, FK -> location_states.id)
           └── City/Town  (location_cities, FK -> location_districts.id)
```

**Source of truth: the database, via the existing read-only API** (`GET /api/v1/locations/states`,
`.../states/{id}/districts`, `.../districts/{id}/cities`). The frontend has no hardcoded district
or city list anywhere — every dropdown resolves through these endpoints. Admin CRUD over the same
tables lives at `/api/v1/location-master/**` (`SUPER_ADMIN`/`FARM_MANAGER` only).

## Migration history (customer-service `db/migration/`, mirrored byte-for-byte into
`backend/app/src/main/resources/db/migration/customer/` for the single-JVM aggregation build)

| Version | What it did |
|---|---|
| V5 | Initial seed: every Indian state/UT, every *pre-2022* district, one city per district (its own headquarters town) |
| V6 | Added `district` to `customer_addresses` (unrelated to location master tables) |
| V8 | Completed Andhra Pradesh to the current 26-district list (13 districts formed in the 2022 reorganization; renamed the pre-existing "Nellore" row in place to "Sri Potti Sriramulu Nellore") |
| **V9** | **This change.** Seeded city/town data for the 13 districts V8 added (each got zero cities from V8 itself) - one headquarters town per district, except Annamayya, which got its full documented municipality/major-town set |

## Andhra Pradesh: current state (verified by direct SQL after V9, not assumed)

- **1** row in `location_states` with code `AP`.
- **26** districts, matching the current (post-2022) official list exactly - verified by name-set
  comparison, not just count.
- **32** cities total across those 26 districts - **14 pre-existing (from V5) + 18 inserted by V9**.
  An earlier draft of this document (and the change's own final report) stated the pre-existing
  count as "13", which undercounted by one: 12 of the 13 pre-2022 districts had exactly **one**
  city each from V5, but **YSR Kadapa had two** ("Kadapa" and "YSR Kadapa") - `12×1 + 1×2 = 14`,
  not 13. `14 + 18 = 32` reconciles exactly; re-verified directly by grouping
  `location_cities.created_at` around V9's own Flyway-recorded apply timestamp
  (`2026-09-26 17:30:59`), not by re-deriving it from the migration file's intent. See "V9's exact
  insertions" below for the full per-district list this produced.
- Every one of the 26 districts has **at least one** city (zero districts with zero cities,
  confirmed by SQL, not assumed from the migration's intent). Annamayya has **6** (highest of any
  district); YSR Kadapa has **2** (unchanged from V5); every other district has **1**.
- Zero duplicate district names, zero duplicate (district, city) pairs, zero orphaned rows (both
  enforced by the schema's own FK/unique constraints, and independently re-verified by SQL).

### V9's exact insertions (18 rows, confirmed by `created_at`, not just read off the migration file)

| District | City |
|---|---|
| Alluri Sitharama Raju | Paderu |
| Anakapalli | Anakapalli |
| Annamayya | B Kothakota |
| Annamayya | Madanapalle |
| Annamayya | Pileru |
| Annamayya | Punganur |
| Annamayya | Rajampet |
| Annamayya | Rayachoti |
| Bapatla | Bapatla |
| Dr. B.R. Ambedkar Konaseema | Amalapuram |
| Eluru | Eluru |
| Kakinada | Kakinada |
| Nandyal | Nandyal |
| NTR | Vijayawada |
| Palnadu | Narasaraopet |
| Parvathipuram Manyam | Parvathipuram |
| Sri Sathya Sai | Puttaparthi |
| Tirupati | Tirupati |

## City/town data scope

This master data is for **customer address selection**, not an administrative gazetteer. It does
**not** include every revenue village or every mandal. For the 13 districts newly seeded in V9,
scope was deliberately kept to:

- **One entry per district: its own official headquarters town** (matching the exact convention
  V5 already established for the original 13 districts - "the district's own name/headquarters is
  its seed city"), **except**
- **Annamayya**, which this task explicitly required to be more complete: its headquarters
  (Madanapalle) plus every other municipality/major town its own official government portal lists
  - Rayachoti, Punganur, and B Kothakota (all confirmed municipalities/nagar panchayats per that
    portal) - plus Pileru and Rajampet, both explicitly required by this task and both confirmed as
    real, government-documented revenue-division headquarters of the district (not municipalities
    by the portal's own category, but genuine, significant, addressable towns - not an invented or
    village-level entry).

### Sources used (fetched 2026-09-26)

- Annamayya municipalities (Madanapalle, B Kothakota, Punganur, Rayachoty) with their own
  pincodes, direct from the official district portal:
  [Municipalities | Annamayya District, Government of Andhra Pradesh](https://annamayya.ap.gov.in/public-utility-category/municipality/)
  (paginated - both pages fetched).
- Annamayya headquarters, revenue divisions (Madanapalle/Pileru/Rayachoti), and the Pileru/
  Rajampet revenue-division-headquarters status:
  [Annamayya district — Wikipedia](https://en.wikipedia.org/wiki/Annamayya_district).
- Each of the other 12 new districts' own headquarters town, confirmed via that district's own
  official AP government portal ("About District"/"District Profile" page) and cross-checked
  against Wikipedia:
  - Alluri Sitharama Raju → Paderu — [Alluri Sitharama Raju district — Wikipedia](https://en.wikipedia.org/wiki/Alluri_Sitharama_Raju_district), [allurisitharamaraju.ap.gov.in](https://allurisitharamaraju.ap.gov.in/history/)
  - Anakapalli → Anakapalli — [anakapalli.ap.gov.in](https://anakapalli.ap.gov.in/about-district/), [Anakapalli district — Wikipedia](https://en.wikipedia.org/wiki/Anakapalli_district)
  - Bapatla → Bapatla — [bapatla.ap.gov.in](https://bapatla.ap.gov.in/about-district/), [Bapatla district — Wikipedia](https://en.wikipedia.org/wiki/Bapatla_district)
  - Dr. B.R. Ambedkar Konaseema → Amalapuram — [konaseema.ap.gov.in](https://konaseema.ap.gov.in/district-profile/), [Konaseema district — Wikipedia](https://en.wikipedia.org/wiki/Konaseema_district)
  - Eluru → Eluru — [eluru.ap.gov.in](https://eluru.ap.gov.in/about-district/)
  - Kakinada → Kakinada — [Kakinada district — Wikipedia](https://en.wikipedia.org/wiki/Kakinada_district)
  - Nandyal → Nandyal — [nandyal.ap.gov.in/municipalities](https://nandyal.ap.gov.in/municipalities/)
  - NTR → Vijayawada — [ntr.ap.gov.in](https://ntr.ap.gov.in/district-profile/), [NTR district — Wikipedia](https://en.wikipedia.org/wiki/NTR_district)
  - Palnadu → Narasaraopet — [Palnadu district — Wikipedia](https://en.wikipedia.org/wiki/Palnadu_district)
  - Parvathipuram Manyam → Parvathipuram — [parvathipurammanyam.ap.gov.in](https://parvathipurammanyam.ap.gov.in/about-district/)
  - Sri Sathya Sai → Puttaparthi — [Sri Sathya Sai district — Wikipedia](https://en.wikipedia.org/wiki/Sri_Sathya_Sai_district)
  - Tirupati → Tirupati (city) — [tirupati.ap.gov.in](https://tirupati.ap.gov.in/district-profile/)

## Canonical naming decisions

- District names follow this project's own required list verbatim (already the current official
  names post-2022; V8's own migration comment documents the pre-2022 → current mapping for every
  district).
- Where a government source's spelling differed slightly from this project's required spelling
  (e.g. the official municipality portal lists "Rayachoty", this project and Wikipedia use
  "Rayachoti"), **this project's given spelling was kept as the canonical display name** and used
  consistently — matching the instruction to pick one spelling and not mix them.

## Pincode handling

`location_cities` has **no pincode column** — `{id, district_id, name, active, created_at,
updated_at}` only, confirmed by reading the entity and the live schema before writing V9. Pincode
has always lived on the customer's own address record (`customer_addresses.pincode`, free text,
entered by the customer/admin), not on the city master. **V9 does not change this.** Some
Annamayya towns have multiple pincodes in reality (Madanapalle 517325, B Kothakota 517370,
Punganur 517247, Rayachoty 516269, per the official portal) — since the schema has nowhere to put
even one canonical pincode per city, none was added; inventing a schema change for this was
explicitly out of scope.

## Important data corrections

- **V8** (prior migration, referenced here for context): renamed the existing "Nellore" district
  row in place to "Sri Potti Sriramulu Nellore" (`UPDATE`, not delete+insert — no FK anywhere
  referenced it, but the row's id was preserved regardless, on principle).
- **V9 makes no corrections to existing rows** — every one of its statements is a pure `INSERT ...
  SELECT ... ON CONFLICT DO NOTHING` against districts V8 already created.

## Existing customer/business addresses were deliberately NOT touched

`customer_addresses` (and farm-service's `business_settings`) store `city`/`district`/`state` as
**plain free-text columns, not foreign keys** into this master data. Farm2Home's own configured
business address is `ST Colony, Yerraguntlapalle, Pileru, Chittoor, Andhra Pradesh - 517214` (its
`district` column literally says `Chittoor`) — predating the 2022 reorganization that moved Pileru
into the new Annamayya district. **This is a known, pre-existing inconsistency, not something V9
introduced or fixed**: the location-master hierarchy now correctly places Pileru under Annamayya
(verified by SQL — see `AndhraPradeshDistrictDataTest.pileruBelongsToAnnamayyaOnly`), but the
business's own free-text address record, and any existing customer's free-text address, still say
whatever district string was typed in at the time and are not automatically rewritten — per this
task's own explicit instruction not to bulk-rewrite free-text address data from a master-data
change, since there is no deterministic way to prove which existing row should move.

## How to add future cities/districts safely

1. Add a new Flyway migration (`V<next>__...sql`) in `customer-service/src/main/resources/db/migration/`
   — **never edit an applied one**. Mirror the exact same file into
   `backend/app/src/main/resources/db/migration/customer/` (the two must stay byte-identical; the
   single-JVM aggregation build reads its own copy).
2. Use `INSERT ... SELECT ... FROM location_states`/`location_districts` (never a literal UUID) so
   the migration works regardless of what random UUIDs a given environment's earlier migrations
   generated.
3. Add `ON CONFLICT (state_id, name) DO NOTHING` (districts) / `ON CONFLICT (district_id, name) DO
   NOTHING` (cities) — the existing unique constraints already prevent duplicates; lean on them.
4. Never `DELETE` a district/city that might already be referenced — prefer `UPDATE` for a name
   correction.
5. Document the real source (government portal / Wikipedia cross-check) in the migration's own
   comments, same as V9 above — don't invent data.
