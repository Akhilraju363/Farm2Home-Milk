-- Geographic routing fields for automatic route selection (see
-- DeliveryRouteSelectionServiceImpl in customer-service, the sole authoritative implementation).
-- Nullable: a route with no geo data is simply never selectable by the automatic algorithm (it
-- falls out of the "has all three fields" filter), but remains fully usable for manual
-- assignment/display - this keeps any pre-existing route from breaking.
ALTER TABLE delivery.delivery_routes
    ADD COLUMN center_latitude  DECIMAL(10, 8),
    ADD COLUMN center_longitude DECIMAL(11, 8),
    ADD COLUMN radius_km        DECIMAL(6, 2);

-- Initial 4-5 routes around Farm2Home's real configured location
-- (13.627088254663684, 78.96885065657058, see farm-service's BusinessSettings) and its 10 KM
-- delivery radius. Central blankets the entire delivery disc (radius 10.5 KM, a small margin over
-- the 10 KM eligibility boundary to avoid a rounding-edge mismatch between this table's radius
-- comparison and the separately-rounded delivery-availability distance) so every address that
-- passes the 10 KM eligibility check is guaranteed to resolve to at least one route - the four
-- directional routes are centered 5 KM out from the farm (radius 7 KM each, comfortably reaching
-- past the 10 KM boundary in their own direction) and win the nearest-center tie-break for
-- addresses actually closer to them, matching DeliveryRouteSelectionServiceImpl's algorithm.
-- area/city/pincode: the entire real delivery area is one ~10 KM rural disc around a single farm
-- location with no distinct named localities in this application's real data - all five routes
-- honestly share the one established nearby city/pincode (see CheckoutPage/DeliveryAddressDialog
-- test data) rather than inventing sub-locality names that don't exist in this system.
INSERT INTO delivery.delivery_routes
    (id, route_name, route_code, area, city, pincode, is_active,
     center_latitude, center_longitude, radius_km, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'Farm2Home Central', 'F2H-CENTRAL', 'Within 10 km of Farm2Home', 'Chikkaballapur', '562101', true,
     13.62708825, 78.96885066, 10.50, now(), now()),
    (gen_random_uuid(), 'Farm2Home North',   'F2H-NORTH',   'Within 10 km of Farm2Home', 'Chikkaballapur', '562101', true,
     13.67199825, 78.96885066, 7.00, now(), now()),
    (gen_random_uuid(), 'Farm2Home East',    'F2H-EAST',    'Within 10 km of Farm2Home', 'Chikkaballapur', '562101', true,
     13.62708825, 79.01506166, 7.00, now(), now()),
    (gen_random_uuid(), 'Farm2Home South',   'F2H-SOUTH',   'Within 10 km of Farm2Home', 'Chikkaballapur', '562101', true,
     13.58217825, 78.96885066, 7.00, now(), now()),
    (gen_random_uuid(), 'Farm2Home West',    'F2H-WEST',    'Within 10 km of Farm2Home', 'Chikkaballapur', '562101', true,
     13.62708825, 78.92263966, 7.00, now(), now());
