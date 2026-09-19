-- The 5 routes seeded in V6__add_route_geography.sql were given city='Chikkaballapur' and
-- pincode='562101' - both wrong. Farm2Home's real address (Pileru, Chittoor, Andhra Pradesh -
-- 517214) is now captured properly in farm-service's business_settings (see farm-service's
-- V7__add_business_address.sql, added in the same change) - that is the single authoritative
-- source for the address text going forward. This migration only corrects the already-applied
-- (wrong) values here; it does not create a live cross-service lookup for them, since a route's
-- own city/pincode are informational/operational fields, not recalculated at read time.
UPDATE delivery.delivery_routes
SET city = 'Pileru',
    pincode = '517214'
WHERE route_code IN ('F2H-CENTRAL', 'F2H-NORTH', 'F2H-EAST', 'F2H-SOUTH', 'F2H-WEST');
